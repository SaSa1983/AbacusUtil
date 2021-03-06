/*
 * Copyright (C) 2015 HaiYang Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.landawn.abacus.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.landawn.abacus.core.MapEntity;
import com.landawn.abacus.exception.AbacusException;
import com.landawn.abacus.exception.UncheckedIOException;
import com.landawn.abacus.logging.Logger;
import com.landawn.abacus.logging.LoggerFactory;
import com.landawn.abacus.parser.DeserializationConfig;
import com.landawn.abacus.parser.Parser;
import com.landawn.abacus.parser.SerializationConfig;
import com.landawn.abacus.type.Type;
import com.landawn.abacus.util.AsyncExecutor;
import com.landawn.abacus.util.ClassUtil;
import com.landawn.abacus.util.IOUtil;
import com.landawn.abacus.util.Maps;
import com.landawn.abacus.util.N;
import com.landawn.abacus.util.NamingPolicy;
import com.landawn.abacus.util.StringUtil;
import com.landawn.abacus.util.function.Function;

/**
 * The client and server communicate by xml/json(may compressed by lz4/snappy/gzip)
 * through http. There are two ways to send the request: <li>1, Send the request
 * with the url. The target web method is identified by request type.</li> <li>
 * 2, Send the request with the url+'/'+operationName. The target web method is
 * identified by operation name in the url.</li>
 * 
 * @since 0.8
 * 
 * @author Haiyang Li
 */
public final class HttpProxy {
    private static final int DEFAULT_MAX_CONNECTION = AbstractHttpClient.DEFAULT_MAX_CONNECTION;
    private static final int DEFAULT_CONNECTION_TIMEOUT = AbstractHttpClient.DEFAULT_CONNECTION_TIMEOUT;
    private static final int DEFAULT_READ_TIMEOUT = AbstractHttpClient.DEFAULT_READ_TIMEOUT;

    // Upper and lower characters, digits, underscores, and hyphens, starting with a character.
    private static final String PARAM = "[a-zA-Z][a-zA-Z0-9_-]*";
    private static final Pattern PARAM_NAME_REGEX = Pattern.compile(PARAM);
    private static final Pattern PARAM_URL_REGEX = Pattern.compile("\\{(" + PARAM + ")\\}");

    public static <T> T createClientProxy(final Class<T> interfaceClass, final ContentFormat contentFormat, final String url) {
        return createClientProxy(interfaceClass, contentFormat, url, DEFAULT_MAX_CONNECTION, DEFAULT_CONNECTION_TIMEOUT, DEFAULT_READ_TIMEOUT);
    }

    public static <T> T createClientProxy(final Class<T> interfaceClass, final ContentFormat contentFormat, final String url, final Config config) {
        return createClientProxy(interfaceClass, contentFormat, url, DEFAULT_MAX_CONNECTION, DEFAULT_CONNECTION_TIMEOUT, DEFAULT_READ_TIMEOUT, config);
    }

    public static <T> T createClientProxy(final Class<T> interfaceClass, final ContentFormat contentFormat, final String url, final int maxConnection) {
        return createClientProxy(interfaceClass, contentFormat, url, maxConnection, DEFAULT_CONNECTION_TIMEOUT, DEFAULT_READ_TIMEOUT);
    }

    public static <T> T createClientProxy(final Class<T> interfaceClass, final ContentFormat contentFormat, final String url, final int maxConnection,
            final long connTimeout, final long readTimeout) {
        return createClientProxy(interfaceClass, contentFormat, url, maxConnection, connTimeout, readTimeout, null);
    }

    public static <T> T createClientProxy(final Class<T> interfaceClass, final ContentFormat contentFormat, final String url, final int maxConnection,
            final long connTimeout, final long readTimeout, final Config config) {

        if (contentFormat == null || contentFormat == ContentFormat.NONE) {
            throw new IllegalArgumentException("Content format can't be null or NONE");
        }

        InvocationHandler h = new InvocationHandler() {
            private final Logger _logger = LoggerFactory.getLogger(interfaceClass);
            private final ContentFormat _contentFormat = contentFormat;
            private final String _url = url;
            private final int _maxConnection = maxConnection;
            private final long _connTimeout = connTimeout;
            private final long _readTimeout = readTimeout;
            private final Config _config = config == null ? new Config() : N.copy(config);

            {
                final Set<Method> declaredMethods = N.asLinkedHashSet(interfaceClass.getDeclaredMethods());

                for (Class<?> superClass : interfaceClass.getInterfaces()) {
                    declaredMethods.addAll(Arrays.asList(superClass.getDeclaredMethods()));
                }

                if (_config.parser == null) {
                    _config.setParser(HTTP.getParser(_contentFormat));
                }

                // set operation configuration.
                final Map<String, OperationConfig> newOperationConfigs = new HashMap<>(N.initHashCapacity(declaredMethods.size()));
                if (config != null && N.notNullOrEmpty(config.operationConfigs)) {
                    for (Map.Entry<String, OperationConfig> entry : config.operationConfigs.entrySet()) {
                        OperationConfig copy = entry.getValue() == null ? new OperationConfig() : N.copy(entry.getValue());

                        if (entry.getValue() != null && entry.getValue().getRequestSettings() != null) {
                            copy.setRequestSettings(entry.getValue().getRequestSettings().copy());
                        }

                        newOperationConfigs.put(entry.getKey(), copy);
                    }
                }
                _config.setOperationConfigs(newOperationConfigs);

                for (Method method : declaredMethods) {
                    final String methodName = method.getName();
                    final Class<?>[] parameterTypes = method.getParameterTypes();
                    final int parameterCount = parameterTypes.length;

                    OperationConfig operationConfig = _config.operationConfigs.get(methodName);

                    if (operationConfig == null) {
                        operationConfig = new OperationConfig();
                        _config.operationConfigs.put(methodName, operationConfig);
                    }

                    operationConfig.requestEntityName = StringUtil.capitalize(methodName) + "Request";
                    operationConfig.responseEntityName = StringUtil.capitalize(methodName) + "Response";

                    RestMethod methodInfo = null;
                    for (Annotation methodAnnotation : method.getAnnotations()) {
                        Class<? extends Annotation> annotationType = methodAnnotation.annotationType();

                        for (Annotation innerAnnotation : annotationType.getAnnotations()) {
                            if (RestMethod.class == innerAnnotation.annotationType()) {
                                methodInfo = (RestMethod) innerAnnotation;

                                break;
                            }
                        }

                        if (methodInfo != null) {
                            if (N.isNullOrEmpty(operationConfig.getRequestUrl())) {
                                try {
                                    String path = (String) annotationType.getMethod("value").invoke(methodAnnotation);

                                    if (N.notNullOrEmpty(path)) {
                                        operationConfig.setRequestUrl(path);
                                    }
                                } catch (Exception e) {
                                    throw new AbacusException("Failed to extract String 'value' from @%s annotation:" + annotationType.getSimpleName());
                                }
                            }

                            if (operationConfig.getHttpMethod() == null) {
                                operationConfig.setHttpMethod(HttpMethod.valueOf(methodInfo.value()));
                            }

                            break;
                        }
                    }

                    if (N.isNullOrEmpty(operationConfig.paramNameTypeMap)) {
                        operationConfig.paramTypes = new Type[parameterCount];
                        operationConfig.paramFields = new Field[parameterCount];
                        operationConfig.paramPaths = new Path[parameterCount];
                        operationConfig.paramNameTypeMap = new HashMap<>();

                        final Annotation[][] parameterAnnotationArrays = method.getParameterAnnotations();
                        for (int i = 0; i < parameterCount; i++) {
                            operationConfig.paramTypes[i] = N.typeOf(parameterTypes[i]);

                            for (Annotation parameterAnnotation : parameterAnnotationArrays[i]) {
                                if (parameterAnnotation.annotationType() == Field.class) {
                                    operationConfig.paramFields[i] = (Field) parameterAnnotation;

                                    if (operationConfig.paramNameTypeMap.put(operationConfig.paramFields[i].value(), operationConfig.paramTypes[i]) != null) {
                                        throw new AbacusException("Duplicated parameter names: " + operationConfig.paramFields[i].value());
                                    }
                                } else if (parameterAnnotation.annotationType() == Path.class) {
                                    operationConfig.validatePathName(((Path) parameterAnnotation).value());

                                    operationConfig.paramPaths[i] = (Path) parameterAnnotation;

                                    if (operationConfig.paramNameTypeMap.put(operationConfig.paramPaths[i].value(), operationConfig.paramTypes[i]) != null) {
                                        throw new AbacusException("Duplicated parameter names: " + operationConfig.paramPaths[i].value());
                                    }
                                }
                            }
                        }
                    }

                    if (operationConfig.httpMethod == null) {
                        operationConfig.httpMethod = HttpMethod.POST;
                    } else if (!(operationConfig.httpMethod == HttpMethod.GET || operationConfig.httpMethod == HttpMethod.POST
                            || operationConfig.httpMethod == HttpMethod.PUT || operationConfig.httpMethod == HttpMethod.DELETE)) {
                        throw new IllegalArgumentException("Unsupported http method: " + operationConfig.httpMethod);
                    }

                    if (parameterCount > 1 && operationConfig.paramNameTypeMap.isEmpty()) {
                        throw new IllegalArgumentException("Unsupported web service method: " + method.getName()
                                + ". Only one parameter or multi parameters with Field/Path annotaions are supported");
                    }

                    if (N.notNullOrEmpty(operationConfig.requestUrl)) {
                        if (StringUtil.startsWithIgnoreCase(operationConfig.requestUrl, "http:")
                                || StringUtil.startsWithIgnoreCase(operationConfig.requestUrl, "https:")) {
                            // no action took
                        } else {
                            if (_url.endsWith("/") || _url.endsWith("\\")) {
                                if (operationConfig.requestUrl.startsWith("/") || operationConfig.requestUrl.startsWith("\\")) {
                                    operationConfig.requestUrl = _url + operationConfig.requestUrl.substring(1);
                                } else {
                                    operationConfig.requestUrl = _url + operationConfig.requestUrl;
                                }
                            } else {
                                if (operationConfig.requestUrl.startsWith("/") || operationConfig.requestUrl.startsWith("\\")) {
                                    operationConfig.requestUrl = _url + operationConfig.requestUrl;
                                } else {
                                    operationConfig.requestUrl = _url + "/" + operationConfig.requestUrl;
                                }
                            }
                        }
                    } else if (_config.requestByOperatioName) {
                        String operationNameUrl = null;

                        if (_config.requestUrlNamingPolicy == NamingPolicy.LOWER_CASE_WITH_UNDERSCORE) {
                            operationNameUrl = ClassUtil.toLowerCaseWithUnderscore(methodName);
                        } else if (_config.requestUrlNamingPolicy == NamingPolicy.UPPER_CASE_WITH_UNDERSCORE) {
                            operationNameUrl = ClassUtil.toUpperCaseWithUnderscore(methodName);
                        } else {
                            operationNameUrl = methodName;
                        }

                        if (_url.endsWith("/") || _url.endsWith("\\")) {
                            operationConfig.requestUrl = _url + operationNameUrl;
                        } else {
                            operationConfig.requestUrl = _url + "/" + operationNameUrl;
                        }

                    } else {
                        operationConfig.requestUrl = _url;
                    }

                    if ((N.notNullOrEmpty(_config.getEncryptionUserName()) || N.notNullOrEmpty(_config.getEncryptionPassword()))
                            && (N.isNullOrEmpty(operationConfig.getEncryptionUserName()) && N.isNullOrEmpty(operationConfig.getEncryptionPassword()))) {
                        if (N.isNullOrEmpty(operationConfig.getEncryptionUserName())) {
                            operationConfig.setEncryptionUserName(_config.getEncryptionUserName());
                        }

                        if (N.isNullOrEmpty(operationConfig.getEncryptionPassword())) {
                            operationConfig.setEncryptionPassword(_config.getEncryptionPassword());
                        }

                        if (operationConfig.getEncryptionMessage() == null) {
                            operationConfig.setEncryptionMessage(_config.getEncryptionMessage());
                        }

                        if (operationConfig.getEncryptionMessage() == null) {
                            operationConfig.setEncryptionMessage(MessageEncryption.NONE);
                        }
                    }
                }

                if (config != null && config.getRequestSettings() != null) {
                    _config.setRequestSettings(config.getRequestSettings().copy());
                }
            }

            private final AtomicInteger sharedActiveConnectionCounter = new AtomicInteger(0);
            private final Map<String, HttpClient> _httpClientPool = new HashMap<>(N.initHashCapacity(_config.operationConfigs.size()));
            private final HttpClient _httpClient = HttpClient.create(_url, _maxConnection, _connTimeout, _readTimeout, _config.getRequestSettings(),
                    sharedActiveConnectionCounter);

            private final AsyncExecutor _asyncExecutor = _config.executedByThreadPool
                    ? (_config.asyncExecutor == null ? new AsyncExecutor() : _config.asyncExecutor) : null;

            @Override
            public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
                // If the method is a method from Object then defer to normal invocation.
                if (method.getDeclaringClass() == Object.class) {
                    return method.invoke(this, args);
                }

                final String methodName = method.getName();
                final OperationConfig operationConfig = _config.operationConfigs.get(methodName);

                if (operationConfig.getRetryTimes() > 0) {
                    try {
                        return invoke(method, args);
                    } catch (Exception e) {
                        _logger.error("Failed to call: " + method.getName(), e);

                        final int retryTimes = operationConfig.getRetryTimes();
                        final long retryInterval = operationConfig.getRetryInterval();
                        final Function<Throwable, Boolean> ifRetry = operationConfig.getIfRetry();

                        int retriedTimes = 0;
                        Throwable throwable = e;

                        while (retriedTimes++ < retryTimes && (ifRetry == null || ifRetry.apply(e).booleanValue())) {
                            try {
                                if (retryInterval > 0) {
                                    N.sleep(retryInterval);
                                }

                                return invoke(method, args);
                            } catch (Exception e2) {
                                throwable = e2;
                            }
                        }

                        throw N.toRuntimeException(throwable);
                    }
                } else {
                    return invoke(method, args);
                }
            }

            private Object invoke(final Method method, final Object[] args) throws InterruptedException, ExecutionException {
                if (_config.executedByThreadPool) {
                    final Callable<Object> cmd = new Callable<Object>() {
                        @Override
                        public Object call() throws Exception {
                            if (_config.handler == null) {
                                return execute(method, args);
                            } else {
                                _config.handler.preInvoke(method, args);

                                Object result = null;
                                Throwable exception = null;

                                try {
                                    result = execute(method, args);
                                } catch (Exception e) {
                                    exception = e;
                                } finally {
                                    result = _config.handler.postInvoke(exception, result, method, args);
                                }

                                return result;
                            }
                        }
                    };

                    return _asyncExecutor.execute(cmd).get();
                } else {
                    if (_config.handler == null) {
                        return execute(method, args);
                    } else {
                        _config.handler.preInvoke(method, args);

                        Object result = null;
                        Throwable exception = null;

                        try {
                            result = execute(method, args);
                        } catch (Exception e) {
                            exception = e;
                        } finally {
                            result = _config.handler.postInvoke(exception, result, method, args);
                        }

                        return result;
                    }
                }
            }

            private Object execute(final Method method, final Object[] args) {
                final String methodName = method.getName();
                final OperationConfig operationConfig = _config.operationConfigs.get(methodName);
                final Object requestParameter = readRequestParameter(args, operationConfig);

                if (N.notNullOrEmpty(operationConfig.getEncryptionUserName()) && N.notNullOrEmpty(operationConfig.getEncryptionPassword())) {
                    ((SecurityDTO) requestParameter).encrypt(operationConfig.getEncryptionUserName(), operationConfig.getEncryptionPassword(),
                            operationConfig.getEncryptionMessage());
                }

                final HttpClient httpClient = getHttpClient(method, requestParameter, operationConfig);
                final Class<T> returnType = (Class<T>) method.getReturnType();

                if (_logger.isInfoEnabled()) {
                    _logger.info(_config.parser.serialize(requestParameter, _config.sc));
                }

                InputStream is = null;
                OutputStream os = null;

                final HttpURLConnection connection = httpClient.openConnection(operationConfig.httpMethod, operationConfig.requestSettings);

                try {
                    if (requestParameter != null && (operationConfig.httpMethod == HttpMethod.POST || operationConfig.httpMethod == HttpMethod.PUT)) {
                        os = httpClient.getOutputStream(connection, _contentFormat);

                        switch (_contentFormat) {
                            case JSON:
                            case JSON_LZ4:
                            case JSON_SNAPPY:
                            case JSON_GZIP:
                                Type<Object> type = N.typeOf(requestParameter.getClass());

                                if (type.isSerializable()) {
                                    os.write(type.stringOf(requestParameter).getBytes());
                                } else {
                                    _config.parser.serialize(os, requestParameter, _config.sc);
                                }

                                break;

                            case XML:
                            case XML_LZ4:
                            case XML_SNAPPY:
                            case XML_GZIP:
                                if (requestParameter instanceof Map) {
                                    _config.parser.serialize(os, MapEntity.valueOf(operationConfig.requestEntityName, (Map<String, Object>) requestParameter),
                                            _config.sc);
                                } else {
                                    _config.parser.serialize(os, requestParameter, _config.sc);
                                }

                                break;

                            case KRYO:
                                _config.parser.serialize(os, requestParameter, _config.sc);

                                break;

                            default:
                                throw new IllegalArgumentException("Unsupported content type: " + _contentFormat.toString());
                        }

                        HTTP.flush(os);
                    } else {
                        String contentType = HTTP.getContentType(_contentFormat);

                        if (N.notNullOrEmpty(contentType)) {
                            connection.setRequestProperty(HttpHeaders.Names.CONTENT_TYPE, contentType);
                        }

                        String contentEncoding = HTTP.getContentEncoding(_contentFormat);

                        if (N.notNullOrEmpty(contentEncoding)) {
                            connection.setRequestProperty(HttpHeaders.Names.CONTENT_ENCODING, contentEncoding);
                        }
                    }

                    final ContentFormat responseContentFormat = httpClient.getContentFormat(connection);
                    final Parser<SerializationConfig<?>, DeserializationConfig<?>> responseParser = responseContentFormat == _contentFormat ? _config.parser
                            : HTTP.getParser(responseContentFormat);

                    is = httpClient.getInputStream(connection, responseContentFormat);

                    if (void.class.equals(returnType)) {
                        return null;
                    } else {
                        T result = null;
                        Type<T> type = null;

                        switch (responseContentFormat) {
                            case JSON:
                            case JSON_LZ4:
                            case JSON_SNAPPY:
                            case JSON_GZIP:

                                type = N.typeOf(returnType);

                                if (type.isSerializable()) {
                                    result = type.valueOf(IOUtil.readString(is));
                                } else {
                                    result = responseParser.deserialize(returnType, is, _config.dc);
                                }

                                break;

                            case XML:
                            case XML_LZ4:
                            case XML_SNAPPY:
                            case XML_GZIP:
                                result = responseParser.deserialize(returnType, is, _config.dc);
                                break;

                            case KRYO:
                                result = responseParser.deserialize(returnType, is, _config.dc);
                                break;

                            default:
                                throw new IllegalArgumentException("Unsupported content type: " + responseContentFormat.toString());
                        }

                        if (_logger.isInfoEnabled()) {
                            if (type != null && type.isSerializable()) {
                                _logger.info(type.stringOf(result));
                            } else {
                                _logger.info(_config.parser.serialize(result, _config.sc));
                            }
                        }

                        return result;
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } finally {
                    httpClient.close(os, is, connection);
                }
            }

            private Object readRequestParameter(final Object[] args, final OperationConfig operationConfig) {
                if (N.isNullOrEmpty(args)) {
                    return null;
                } else if (operationConfig.paramNameTypeMap.isEmpty()) {
                    return args[0];
                } else {
                    final Map<String, Object> parameterMap = new HashMap<>();

                    for (int i = 0, len = args.length; i < len; i++) {
                        if (operationConfig.paramFields[i] != null) {
                            parameterMap.put(operationConfig.paramFields[i].value(), args[i]);
                        } else if (operationConfig.paramPaths[i] != null) {
                            parameterMap.put(operationConfig.paramPaths[i].value(), args[i] == null ? "null"
                                    : (operationConfig.paramPaths[i].encode() ? N.urlEncode(N.stringOf(args[i])) : N.stringOf(args[i])));
                        }
                    }

                    return parameterMap;
                }
            }

            private HttpClient getHttpClient(final Method method, final Object parameter, final OperationConfig operationConfig) {
                final String methodName = method.getName();
                HttpClient httpClient = null;

                if (operationConfig.httpMethod == HttpMethod.POST || operationConfig.httpMethod == HttpMethod.PUT || parameter == null) {
                    synchronized (_httpClientPool) {
                        httpClient = _httpClientPool.get(methodName);

                        if (httpClient == null) {
                            if (operationConfig.requestUrl.equals(_url)) {
                                httpClient = _httpClient;
                            } else {
                                httpClient = HttpClient.create(operationConfig.requestUrl, _maxConnection, _connTimeout, _readTimeout,
                                        _config.getRequestSettings(), sharedActiveConnectionCounter);
                            }

                            _httpClientPool.put(methodName, httpClient);
                        }
                    }
                } else {
                    final Type<?> type = N.typeOf(parameter.getClass());
                    String requestUrl = operationConfig.requestUrl;

                    if (N.notNullOrEmpty(operationConfig.requestUrlParamNames)) {
                        final Map<String, Object> m = (Map<String, Object>) parameter;

                        for (Map.Entry<String, String> entry : operationConfig.requestUrlParamNames.entrySet()) {
                            requestUrl = requestUrl.replace(entry.getValue(), m.remove(entry.getKey()).toString());
                        }
                    }

                    if (type.isMap() && ((Map<String, Object>) parameter).size() == 0) {
                        // ignore.
                    } else {
                        if (_config.requestParamNamingPolicy == null || _config.requestParamNamingPolicy == NamingPolicy.LOWER_CAMEL_CASE) {
                            requestUrl = requestUrl + "?" + N.urlEncode(parameter);
                        } else {
                            Object newParameter = parameter;

                            if (type.isEntity()) {
                                newParameter = Maps.entity2Map(parameter, !N.isDirtyMarker(parameter.getClass()), null, _config.requestParamNamingPolicy);
                            } else if (type.isMap()) {
                                final Map<String, Object> m = ((Map<String, Object>) parameter);
                                final Map<String, Object> newMap = new LinkedHashMap<>();

                                switch (_config.requestParamNamingPolicy) {
                                    case LOWER_CASE_WITH_UNDERSCORE:
                                    case UPPER_CASE_WITH_UNDERSCORE: {
                                        for (Map.Entry<String, Object> entry : m.entrySet()) {
                                            newMap.put(_config.requestParamNamingPolicy.convert(entry.getKey()), entry.getValue());
                                        }
                                        break;
                                    }

                                    case LOWER_CAMEL_CASE: {
                                        newMap.putAll(m);
                                        break;
                                    }

                                    default:
                                        throw new IllegalArgumentException("Unsupported Naming policy: " + _config.requestParamNamingPolicy);
                                }

                                newParameter = newMap;
                            }

                            requestUrl = requestUrl + "?" + N.urlEncode(newParameter);
                        }
                    }

                    httpClient = HttpClient.create(requestUrl, _maxConnection, _connTimeout, _readTimeout, _config.getRequestSettings(),
                            sharedActiveConnectionCounter);
                }

                return httpClient;
            }
        };

        return (T) N.newProxyInstance(N.asArray(interfaceClass), h);
    }

    /**
     * Gets the set of unique path parameters used in the given URI. If a parameter is used twice
     * in the URI, it will only show up once in the set.
     */
    static Set<String> parsePathParameters(final String path) {
        Matcher m = PARAM_URL_REGEX.matcher(path);
        Set<String> patterns = new LinkedHashSet<>();
        while (m.find()) {
            patterns.add(m.group(1));
        }
        return patterns;
    }

    public static final class Config {
        private Parser<SerializationConfig<?>, DeserializationConfig<?>> parser;
        private SerializationConfig<?> sc;
        private DeserializationConfig<?> dc;
        private Handler handler;

        private boolean executedByThreadPool;
        private AsyncExecutor asyncExecutor;

        private HttpSettings requestSettings;

        private Map<String, OperationConfig> operationConfigs;
        private boolean requestByOperatioName;
        private NamingPolicy requestUrlNamingPolicy;
        private NamingPolicy requestParamNamingPolicy;
        private String encryptionUserName;
        private byte[] encryptionPassword;
        private MessageEncryption encryptionMessage;

        public Parser<SerializationConfig<?>, DeserializationConfig<?>> getParser() {
            return parser;
        }

        public Config setParser(final Parser<SerializationConfig<?>, DeserializationConfig<?>> parser) {
            this.parser = parser;

            return this;
        }

        public SerializationConfig<?> getSerializationConfig() {
            return sc;
        }

        public Config setSerializationConfig(final SerializationConfig<?> sc) {
            this.sc = sc;

            return this;
        }

        public DeserializationConfig<?> getDeserializationConfig() {
            return dc;
        }

        public Config setDeserializationConfig(final DeserializationConfig<?> dc) {
            this.dc = dc;

            return this;
        }

        public boolean isExecutedByThreadPool() {
            return executedByThreadPool;
        }

        public Config setExecutedByThreadPool(final boolean executedByThreadPool) {
            this.executedByThreadPool = executedByThreadPool;

            return this;
        }

        public AsyncExecutor getAsyncExecutor() {
            return asyncExecutor;
        }

        public Config setAsyncExecutor(final AsyncExecutor asyncExecutor) {
            this.asyncExecutor = asyncExecutor;

            return this;
        }

        public Handler getHandler() {
            return handler;
        }

        public Config setHandler(final Handler handler) {
            this.handler = handler;

            return this;
        }

        public HttpSettings getRequestSettings() {
            return requestSettings;
        }

        public Config setRequestSettings(final HttpSettings requestSettings) {
            this.requestSettings = requestSettings;

            return this;
        }

        public boolean isRequestByOperatioName() {
            return requestByOperatioName;
        }

        public Config setRequestByOperatioName(final boolean requestByOperatioName) {
            this.requestByOperatioName = requestByOperatioName;

            return this;
        }

        public NamingPolicy getRequestUrlNamingPolicy() {
            return requestUrlNamingPolicy;
        }

        public Config setRequestUrlNamingPolicy(final NamingPolicy requestUrlNamingPolicy) {
            this.requestUrlNamingPolicy = requestUrlNamingPolicy;

            return this;
        }

        public NamingPolicy getRequestParamNamingPolicy() {
            return requestParamNamingPolicy;
        }

        public Config setRequestParamNamingPolicy(final NamingPolicy requestParamNamingPolicy) {
            this.requestParamNamingPolicy = requestParamNamingPolicy;

            return this;
        }

        public Map<String, OperationConfig> getOperationConfigs() {
            return operationConfigs;
        }

        public Config setOperationConfigs(final Map<String, OperationConfig> operationConfigs) {
            this.operationConfigs = operationConfigs;

            return this;
        }

        public String getEncryptionUserName() {
            return encryptionUserName;
        }

        /**
         * The target request DTOs must inherit from <code>SecurityDTO</code>.
         * 
         * @param encryptionUserName
         * @return
         * @see SecurityDTO#encrypt(String, String)
         */
        public Config setEncryptionUserName(final String encryptionUserName) {
            this.encryptionUserName = encryptionUserName;

            return this;
        }

        public byte[] getEncryptionPassword() {
            return encryptionPassword;
        }

        /**
         * The target request DTOs must inherit from <code>SecurityDTO</code>.
         * 
         * @param encryptionPassword
         * @return
         * @see SecurityDTO#encrypt(String, String)
         */
        public Config setEncryptionPassword(final byte[] encryptionPassword) {
            this.encryptionPassword = encryptionPassword;

            return this;
        }

        public MessageEncryption getEncryptionMessage() {
            return encryptionMessage;
        }

        /**
         * The target request DTOs must inherit from <code>SecurityDTO</code>.
         * 
         * @param encryptionMessage
         * @return
         * @see SecurityDTO#encrypt(String, String)
         */
        public Config setEncryptionMessage(final MessageEncryption encryptionMessage) {
            this.encryptionMessage = encryptionMessage;

            return this;
        }

        @Override
        public String toString() {
            return "{parser=" + parser + ", sc=" + sc + ", dc=" + dc + ", handler=" + handler + ", executedByThreadPool=" + executedByThreadPool
                    + ", asyncExecutor=" + asyncExecutor + ", requestSettings=" + requestSettings + ", requestByOperatioName=" + requestByOperatioName
                    + ", requestUrlNamingPolicy=" + requestUrlNamingPolicy + ", requestParamNamingPolicy=" + requestParamNamingPolicy + ", operationConfigs="
                    + operationConfigs + "}";
        }
    }

    public static class OperationConfig {
        private String requestUrl;
        private HttpMethod httpMethod;
        private HttpSettings requestSettings;
        private int retryTimes;
        private long retryInterval;
        private Function<Throwable, Boolean> ifRetry;
        private String encryptionUserName;
        private byte[] encryptionPassword;
        private MessageEncryption encryptionMessage;

        String requestEntityName;
        String responseEntityName;

        Map<String, String> requestUrlParamNames;
        Type<?>[] paramTypes;
        Field[] paramFields;
        Path[] paramPaths;
        Map<String, Type<?>> paramNameTypeMap;

        public String getRequestUrl() {
            return requestUrl;
        }

        public OperationConfig setRequestUrl(final String requestUrl) {
            this.requestUrl = requestUrl;
            requestUrlParamNames = new LinkedHashMap<>();

            Matcher m = PARAM_URL_REGEX.matcher(requestUrl);
            while (m.find()) {
                requestUrlParamNames.put(m.group(1), m.group());
            }

            return this;
        }

        public HttpMethod getHttpMethod() {
            return httpMethod;
        }

        public OperationConfig setHttpMethod(final HttpMethod httpMethod) {
            this.httpMethod = httpMethod;

            return this;
        }

        public HttpSettings getRequestSettings() {
            return requestSettings;
        }

        public OperationConfig setRequestSettings(final HttpSettings requestSettings) {
            this.requestSettings = requestSettings;

            return this;
        }

        public int getRetryTimes() {
            return retryTimes;
        }

        public OperationConfig setRetryTimes(final int retryTimes) {
            this.retryTimes = retryTimes;

            return this;
        }

        public long getRetryInterval() {
            return retryInterval;
        }

        public OperationConfig setRetryInterval(final long retryInterval) {
            this.retryInterval = retryInterval;

            return this;
        }

        public Function<Throwable, Boolean> getIfRetry() {
            return ifRetry;
        }

        public OperationConfig setIfRetry(final Function<Throwable, Boolean> ifRetry) {
            this.ifRetry = ifRetry;

            return this;
        }

        public String getEncryptionUserName() {
            return encryptionUserName;
        }

        /**
         * The target request DTOs must inherit from <code>SecurityDTO</code>.
         * 
         * @param encryptionUserName
         * @return
         * @see SecurityDTO#encrypt(String, String)
         */
        public OperationConfig setEncryptionUserName(final String encryptionUserName) {
            this.encryptionUserName = encryptionUserName;

            return this;
        }

        public byte[] getEncryptionPassword() {
            return encryptionPassword;
        }

        /**
         * The target request DTOs must inherit from <code>SecurityDTO</code>.
         * 
         * @param encryptionPassword
         * @return
         * @see SecurityDTO#encrypt(String, String)
         */
        public OperationConfig setEncryptionPassword(final byte[] encryptionPassword) {
            this.encryptionPassword = encryptionPassword;

            return this;
        }

        public MessageEncryption getEncryptionMessage() {
            return encryptionMessage;
        }

        /**
         * The target request DTOs must inherit from <code>SecurityDTO</code>.
         * 
         * @param encryptionMessage
         * @return
         * @see SecurityDTO#encrypt(String, String)
         */
        public OperationConfig setEncryptionMessage(final MessageEncryption encryptionMessage) {
            this.encryptionMessage = encryptionMessage;

            return this;
        }

        /**
         * copied from retrofit under the Apache License, Version 2.0 (the "License");
         * 
         * @param index
         * @param name
         */
        void validatePathName(final String name) {
            if (!PARAM_NAME_REGEX.matcher(name).matches()) {
                throw new AbacusException(String.format("@Path parameter name must match %s. Found: %s", PARAM_URL_REGEX.pattern(), name));
            }

            // Verify URL replacement name is actually present in the URL path.
            if (!requestUrlParamNames.containsKey(name)) {
                throw new AbacusException(String.format("URL \"%s\" does not contain \"{%s}\".", requestUrl, name));
            }
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((httpMethod == null) ? 0 : httpMethod.hashCode());
            result = prime * result + ((requestUrl == null) ? 0 : requestUrl.hashCode());
            result = prime * result + ((requestSettings == null) ? 0 : requestSettings.hashCode());
            result = prime * result + retryTimes;
            result = prime * result + (int) retryInterval;
            result = prime * result + ((ifRetry == null) ? 0 : ifRetry.hashCode());
            result = prime * result + ((encryptionUserName == null) ? 0 : encryptionUserName.hashCode());
            result = prime * result + ((encryptionPassword == null) ? 0 : N.hashCode(encryptionPassword));
            result = prime * result + ((encryptionMessage == null) ? 0 : encryptionMessage.hashCode());
            return result;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }

            if (obj instanceof OperationConfig) {
                OperationConfig other = (OperationConfig) obj;

                return N.equals(httpMethod, other.httpMethod) && N.equals(requestUrl, other.requestUrl) && N.equals(requestSettings, other.requestSettings)
                        && N.equals(retryTimes, other.retryTimes) && N.equals(retryInterval, other.retryInterval) && N.equals(ifRetry, other.ifRetry)
                        && N.equals(encryptionUserName, other.encryptionUserName) && N.equals(encryptionPassword, other.encryptionPassword)
                        && N.equals(encryptionMessage, other.encryptionMessage);
            }

            return false;
        }

        @Override
        public String toString() {
            return "{httpMethod=" + httpMethod + ", requestUrl=" + requestUrl + ", requestSettings=" + requestSettings + ", retryTimes=" + retryTimes
                    + ", retryInterval=" + retryInterval + ", ifRetry=" + ifRetry + "}";
        }
    }

    public static interface Handler {
        void preInvoke(final Method method, final Object... args);

        Object postInvoke(final Throwable e, final Object result, final Method method, final Object... args);
    }
}
