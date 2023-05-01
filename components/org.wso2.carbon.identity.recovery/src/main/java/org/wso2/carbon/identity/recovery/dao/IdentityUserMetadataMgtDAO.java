/*
 * Copyright (c) 2023, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.recovery.dao;

import org.wso2.carbon.identity.event.IdentityEventException;
import org.wso2.carbon.identity.governance.model.UserIdentityClaim;
import org.wso2.carbon.user.core.UserStoreManager;

/**
 * This interface is used to access the data storage to retrieve and store identity user metadata.
 */
public interface IdentityUserMetadataMgtDAO {

    void updateUserMetadata(UserStoreManager userStoreManager, String username,
                            String claimURI, String value, String eventName) throws IdentityEventException;

    UserIdentityClaim loadUserMetadataFromCache(UserStoreManager userStoreManager, String username);

    void storeUserMetadataToCache(UserStoreManager userStoreManager, UserIdentityClaim userIdentityDTO);
}
