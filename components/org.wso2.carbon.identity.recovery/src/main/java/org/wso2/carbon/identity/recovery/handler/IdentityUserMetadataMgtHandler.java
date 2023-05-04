/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
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

package org.wso2.carbon.identity.recovery.handler;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.base.IdentityRuntimeException;
import org.wso2.carbon.identity.core.bean.context.MessageContext;
import org.wso2.carbon.identity.core.handler.InitConfig;
import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.IdentityEventException;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.event.handler.AbstractEventHandler;
import org.wso2.carbon.identity.governance.model.UserIdentityClaim;
import org.wso2.carbon.identity.mgt.constants.IdentityMgtConstants;
import org.wso2.carbon.identity.recovery.IdentityRecoveryConstants;
import org.wso2.carbon.identity.recovery.dao.IdentityUserMetadataMgtDAO;
import org.wso2.carbon.identity.recovery.dao.impl.IdentityUserMetadataMgtDAOImpl;
import org.wso2.carbon.identity.recovery.util.Utils;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.UserStoreManager;

import java.util.HashMap;
import java.util.Map;

/**
 * This event handler is used to handle events related to user meta data updates.
 */
public class IdentityUserMetadataMgtHandler extends AbstractEventHandler {

    private static final Log log = LogFactory.getLog(IdentityUserMetadataMgtHandler.class);
    private static final String POST_AUTHENTICATION = "post_authentication";
    private static final String POST_CREDENTIAL_UPDATE = "post_credential_update";
    private static final String ENABLE_IDENTITY_USER_METADATA_MGT_HANDLER = "identityUserMetadataMgtHandler.enable";
    private static final String USE_DAO_FOR_USER_METADATA_UPDATE = "identityUserMetadataMgtHandler.enableDAO";

    private final IdentityUserMetadataMgtDAO identityUserMetadataMgtDAO = new IdentityUserMetadataMgtDAOImpl();

    @Override
    public void handleEvent(Event event) throws IdentityEventException {

        Map<String, Object> eventProperties = event.getEventProperties();
        UserStoreManager userStoreManager = (UserStoreManager)
                eventProperties.get(IdentityEventConstants.EventProperty.USER_STORE_MANAGER);

        boolean enable = Boolean.parseBoolean(configs.getModuleProperties().getProperty(
                ENABLE_IDENTITY_USER_METADATA_MGT_HANDLER));
        boolean enableDao = Boolean.parseBoolean(configs.getModuleProperties().getProperty(
                USE_DAO_FOR_USER_METADATA_UPDATE));
        if (!enable) {
            if (log.isDebugEnabled()) {
                log.debug("Identity User Metadata Management handler is not enabled.");
            }
            return;
        }
        if (IdentityEventConstants.Event.POST_AUTHENTICATION.equals(event.getEventName())) {
            handlePostAuthenticate(eventProperties, userStoreManager, enableDao);
        } else if (IdentityEventConstants.Event.POST_UPDATE_CREDENTIAL.equals(event.getEventName()) ||
                IdentityEventConstants.Event.POST_UPDATE_CREDENTIAL_BY_ADMIN.equals(event.getEventName())) {
            handleCredentialUpdate(eventProperties, userStoreManager, enableDao);
        }
    }

    private void handlePostAuthenticate(Map<String, Object> eventProperties, UserStoreManager userStoreManager,
                                        boolean enableDao) throws IdentityEventException {

        if (log.isDebugEnabled()) {
            log.debug("Start handling post authentication event.");
        }
        if ((Boolean) eventProperties.get(IdentityEventConstants.EventProperty.OPERATION_STATUS)) {
            String lastLoginTime = Long.toString(System.currentTimeMillis());
            if (enableDao) {
                String username = Utils.buildUserNameWithDomain(userStoreManager, eventProperties);
                if (username.contains(IdentityRecoveryConstants.TENANT_ASSOCIATION_MANAGER)) {
                    return;
                }
                // Update data in db.
                identityUserMetadataMgtDAO.updateUserMetadata(userStoreManager, username,
                        IdentityMgtConstants.LAST_LOGIN_TIME, lastLoginTime, POST_AUTHENTICATION);
                // Update cache.
                updateUserIdentityCache(userStoreManager, username, IdentityMgtConstants.LAST_LOGIN_TIME, lastLoginTime);
                return;
            }
            setUserClaim(userStoreManager, eventProperties, IdentityMgtConstants.LAST_LOGIN_TIME,
                    lastLoginTime, POST_AUTHENTICATION);
        }
    }

    private void handleCredentialUpdate(Map<String, Object> eventProperties, UserStoreManager userStoreManager,
                                        boolean enableDao) throws IdentityEventException {

        if (log.isDebugEnabled()) {
            log.debug("Start handling post credential update event.");
        }
        String lastPasswordUpdateTime = Long.toString(System.currentTimeMillis());
        if (enableDao) {
            String username = Utils.buildUserNameWithDomain(userStoreManager, eventProperties);
            if (username.contains(IdentityRecoveryConstants.TENANT_ASSOCIATION_MANAGER)) {
                return;
            }
            // update data in db
            identityUserMetadataMgtDAO.updateUserMetadata(userStoreManager, username,
                    IdentityMgtConstants.LAST_PASSWORD_UPDATE_TIME, lastPasswordUpdateTime, POST_CREDENTIAL_UPDATE);
            //update cache
            updateUserIdentityCache(userStoreManager, username, IdentityMgtConstants.LAST_PASSWORD_UPDATE_TIME,
                    lastPasswordUpdateTime);
            return;
        }
        setUserClaim(userStoreManager, eventProperties, IdentityMgtConstants.LAST_PASSWORD_UPDATE_TIME,
                lastPasswordUpdateTime, POST_CREDENTIAL_UPDATE);
    }

    private void setUserClaim(UserStoreManager userStoreManager, Map<String, Object> eventProperties,
                              String claimURI, String claimValue, String eventName) throws IdentityEventException {

        String username = (String) eventProperties.get(IdentityEventConstants.EventProperty.USER_NAME);
        Map<String, String> userClaims = new HashMap<>();
        userClaims.put(claimURI, claimValue);
        try {
            userStoreManager.setUserClaimValues(username, userClaims, null);
            if (log.isDebugEnabled()) {
                log.debug(String.format("Successfully updated the user claims related to %s event.", eventName));
            }
        } catch (UserStoreException e) {
            throw new IdentityEventException(
                    String.format("Error occurred while updating user claims related to %s event.", eventName), e);
        }
    }

    private void updateUserIdentityCache(UserStoreManager userStoreManager, String username,
                                         String claimURI, String claimValue) {

        UserIdentityClaim identityClaimsFromCache =
                identityUserMetadataMgtDAO.loadUserMetadataFromCache(userStoreManager, username);
        if (identityClaimsFromCache == null) {
            identityClaimsFromCache = new UserIdentityClaim(username);
        }
        identityClaimsFromCache.setUserIdentityDataClaim(claimURI, claimValue);
        identityUserMetadataMgtDAO.storeUserMetadataToCache(userStoreManager, identityClaimsFromCache);
    }

    @Override
    public String getName() {

        return "identityUserMetadataMgtHandler";
    }

    @Override
    public int getPriority(MessageContext messageContext) {

        return 50;
    }

    @Override
    public void init(InitConfig configuration) throws IdentityRuntimeException {

        super.init(configuration);
    }

    public String getFriendlyName() {

        return "Identity User Metadata Management Handler";
    }
}
