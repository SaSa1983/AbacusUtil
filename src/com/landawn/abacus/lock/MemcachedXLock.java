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

package com.landawn.abacus.lock;

import java.io.Serializable;

import com.landawn.abacus.LockMode;
import com.landawn.abacus.logging.Logger;
import com.landawn.abacus.logging.LoggerFactory;
import com.landawn.abacus.util.DateUtil;
import com.landawn.abacus.util.MemcachedLock;
import com.landawn.abacus.util.N;

/**
 * 
 * @since 0.8
 * 
 * @author Haiyang Li
 */
public class MemcachedXLock<T> extends AbstractXLock<T> {
    private static final Logger logger = LoggerFactory.getLogger(MemcachedXLock.class);

    static final long DEFAULT_LIVE_TIME = 3600 * 1000L;

    /**
     * Field liveTime. unit is milliseconds
     */
    private final long liveTime;

    /**
     * Field timeout. unit is milliseconds
     */
    private final long timeout;

    /**
     * MUST NOT CHANGE THE VALUE.
     * 
     */
    private final String keyPrefix;

    private final MemcachedLock<String, Object> mLock;

    public MemcachedXLock(String servers) {
        this(servers, N.EMPTY_STRING, DEFAULT_LIVE_TIME, DEFAULT_TIMEOUT);
    }

    public MemcachedXLock(String servers, String keyPrefix, String liveTime) {
        this(servers, keyPrefix, Long.valueOf(liveTime), DEFAULT_TIMEOUT);
    }

    public MemcachedXLock(String servers, String keyPrefix, long liveTime, long timeout) {
        this.mLock = new MemcachedLock<String, Object>(servers);

        this.liveTime = liveTime;
        this.timeout = timeout;

        this.keyPrefix = keyPrefix;
    }

    @Override
    public String lock(T target, LockMode lockMode, String refLockCode) {
        return lock(target, lockMode, refLockCode, timeout);
    }

    @Override
    public String lock(T target, LockMode lockMode, String refLockCode, long timeout) {
        checkTargetObject(target);
        checkLockMode(lockMode);

        if (refLockCode == null) {
            refLockCode = N.uuid();
        }

        final long endTime = System.currentTimeMillis() + timeout;

        final String key = generateKey(keyPrefix, target);

        final LockInfo lockInfo = new LockInfo();
        lockInfo.lockCode = refLockCode;
        lockInfo.lockMode = lockMode;

        do {
            if (mLock.lock(key, lockInfo, liveTime)) {
                return refLockCode;
            } else {
                N.sleep(1);
            }
        } while ((endTime - DateUtil.currentMillis()) > 0);

        return null;

        //        if (mLock.lock(key, lockInfo, liveTime)) {
        //            return refLockCode;
        //        }
        //
        //        final String orderInKey = generateKey(orderInKeyPrefix, target);
        //        final String orderOutKey = generateKey(orderOutKeyPrefix, target);
        //
        //        final long orderInNumber = mLock.client().increment(orderInKey, 1, 1, liveTime);
        //
        //        if (orderInNumber <= 0) {
        //            return null;
        //        }
        //
        //        Object orderOutNumber = null;
        //
        //        try {
        //            do {
        //                orderOutNumber = mLock.client().get(orderOutKey);
        //
        //                if ((orderOutNumber == null) || ((orderInNumber - N.parseLong(orderOutNumber.toString())) <= 1)) {
        //                    if (mLock.lock(key, lockInfo, liveTime)) {
        //                        return refLockCode;
        //                    }
        //                } else {
        //                    N.sleep(10);
        //                }
        //            } while ((endTime - N.currentMillis()) > 0);
        //        } finally {
        //            mLock.client().increment(orderOutKey, 1, 1, liveTime);
        //        }
        //
        //        return null;
    }

    @Override
    public boolean isLocked(T target, LockMode requiredLockMode, String refLockCode) {
        checkTargetObject(target);
        checkLockMode(requiredLockMode);

        final String key = generateKey(keyPrefix, target);

        LockInfo lockInfo = (LockInfo) mLock.client().get(key);

        if (lockInfo == null || lockInfo.lockCode == null || N.equals(lockInfo.lockCode, refLockCode)) {
            return false;
        }

        final LockMode lockMode = lockInfo == null ? null : lockInfo.lockMode;

        return lockMode != null && lockMode.isXLockOf(requiredLockMode);
    }

    @Override
    public boolean unlock(T target, String refLockCode) {
        checkTargetObject(target);

        final String key = generateKey(keyPrefix, target);

        LockInfo lockInfo = (LockInfo) mLock.client().get(key);

        if (lockInfo == null) {
            return true;
        }

        if (N.equals(lockInfo.lockCode, refLockCode)) {
            try {
                return mLock.client().delete(key);
            } catch (Exception e) {
                if (mLock.client() == null) {
                    return true;
                } else {
                    if (logger.isWarnEnabled()) {
                        logger.warn("Failed to unlock with key: " + key, e);
                    }

                    return false;
                }
            }
        } else {
            return false;
        }
    }

    protected String generateKey(String keyPrefix, T obj) {
        return N.isNullOrEmpty(keyPrefix) ? N.base64Encode(N.stringOf(obj).getBytes()) : (keyPrefix + N.base64Encode(N.stringOf(obj).getBytes()));
    }

    /**
     * @author Haiyang Li
     * 
     * @version $Revision: 0.8 $ 07/09/08
     */
    private static class LockInfo implements Serializable {
        private static final long serialVersionUID = -8960408497897352419L;

        private String lockCode;
        private LockMode lockMode;
    }
}
