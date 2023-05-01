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

package org.wso2.carbon.identity.idle.account.identification.dao.impl;

import org.apache.commons.lang.StringUtils;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.identity.core.util.IdentityDatabaseUtil;
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.identity.idle.account.identification.constants.IdleAccIdentificationConstants;
import org.wso2.carbon.identity.idle.account.identification.dao.IdleAccIdentificationDAO;
import org.wso2.carbon.identity.idle.account.identification.exception.IdleAccIdentificationException;
import org.wso2.carbon.identity.idle.account.identification.exception.IdleAccIdentificationServerException;
import org.wso2.carbon.identity.idle.account.identification.internal.IdleAccountIdentificationDataHolder;
import org.wso2.carbon.identity.idle.account.identification.models.InactiveUserModel;
import org.wso2.carbon.identity.idle.account.identification.util.Utils;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.UserStoreManager;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.user.core.util.UserCoreUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implementation class for IdleAccIdentificationDAO.
 */
public class IdleAccIdentificationDAOImpl implements IdleAccIdentificationDAO {

    @Override
    public List<InactiveUserModel> getInactiveUsers(String inactiveAfter, String excludeBefore, String tenantDomain)
            throws IdleAccIdentificationException {

        RealmService realmService = IdleAccountIdentificationDataHolder.getInstance().getRealmService();

//        TODO: retrieve correct realm service
//        RealmService realmService1 = UserCoreUtil.getRealmService();
//
//        PrivilegedCarbonContext.startTenantFlow();
//        PrivilegedCarbonContext privilegedCarbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
//        privilegedCarbonContext.setTenantId(IdentityTenantUtil.getTenantId(tenantDomain));
//        privilegedCarbonContext.setTenantDomain(tenantDomain);
//
//        UserStoreManager userStoreManager;
//        try {
//            userStoreManager = (UserStoreManager) CarbonContext.getThreadLocalCarbonContext().
//                    getUserRealm().getUserStoreManager();
//        } catch (UserStoreException e) {
//            throw new RuntimeException(e);
//        }

        if (StringUtils.isEmpty(excludeBefore)) {
            return getInactiveUsersFromSpecificDate(inactiveAfter, tenantDomain, realmService);
        } else {
            return getLimitedInactiveUsersFromSpecificDate(inactiveAfter, excludeBefore, tenantDomain, realmService);
        }
    }

    /**
     * Get inactive users from a specific date.
     *
     * @param inactiveAfter date after which the user should be inactive.
     * @param tenantDomain tenant domain.
     * @return list of inactive users.
     * @throws IdleAccIdentificationException Exception when retrieving inactive users from database.
     */
    private List<InactiveUserModel> getInactiveUsersFromSpecificDate(String inactiveAfter, String tenantDomain,
                                                                     RealmService realmService) throws IdleAccIdentificationException {

        String sqlStmt = IdleAccIdentificationConstants.SQLConstants.GET_INACTIVE_USERS_FROM_SPECIFIC_DATE;
        int tenantId = IdentityTenantUtil.getTenantId(tenantDomain);
        String epoch = Utils.convertDateIntoEpoch(inactiveAfter);
        List<InactiveUserModel> inactiveUsers = new ArrayList<>();
        try (Connection connection = IdentityDatabaseUtil.getDBConnection(true);) {
            try (PreparedStatement prepStmt = connection.prepareStatement(sqlStmt)) {
                prepStmt.setString(1, IdleAccIdentificationConstants.LAST_LOGIN_TIME_CLAIM);
                prepStmt.setInt(2, tenantId);
                prepStmt.setString(3, epoch);
                try (ResultSet resultSet = prepStmt.executeQuery()) {
                    while (resultSet.next()) {
                        String username = resultSet.getString(1);
                        if (StringUtils.isNotBlank(username)) {
//                            inactiveUsers.add(username);
//                            inactiveUsers.add(fetchUserEmail(tenantId, realmService, username));
                            InactiveUserModel user = new InactiveUserModel();
                            user.setUsername(username);
                            user.setUserStoreDomain(UserCoreUtil.extractDomainFromName(username));
                            user.setEmail(fetchUserEmail(tenantId, realmService, username));
                            inactiveUsers.add(user);
                        }
                    }
                }
                IdentityDatabaseUtil.commitTransaction(connection);
                return inactiveUsers;
            }

        } catch (SQLException e) {
            throw new IdleAccIdentificationServerException(
                    IdleAccIdentificationConstants.ErrorMessages.ERROR_RETRIEVE_INACTIVE_USERS_FROM_DB.getCode(),
                    IdleAccIdentificationConstants.ErrorMessages.ERROR_RETRIEVE_INACTIVE_USERS_FROM_DB.getMessage());
        }
    }

    /**
     * Get inactive users from a specific date excluding the oldest inactive users.
     *
     * @param inactiveAfter date after which the user should be inactive.
     * @param excludeBefore date before which the user should be excluded.
     * @param tenantDomain tenant domain.
     * @return list of inactive users.
     * @throws IdleAccIdentificationException Exception when retrieving inactive users from database.
     */
    private List<InactiveUserModel> getLimitedInactiveUsersFromSpecificDate(String inactiveAfter, String excludeBefore,
                                                                            String tenantDomain, RealmService realmService) throws IdleAccIdentificationException {

        String sqlStmt = IdleAccIdentificationConstants.SQLConstants.GET_LIMITED_INACTIVE_USERS_FROM_SPECIFIC_DATE;
        int tenantId = IdentityTenantUtil.getTenantId(tenantDomain);
        String inactiveDateEpoch = Utils.convertDateIntoEpoch(inactiveAfter);
        String excludeDateEpoch = Utils.convertDateIntoEpoch(excludeBefore);
        List<InactiveUserModel> inactiveUsers = new ArrayList<>();
        try (Connection connection = IdentityDatabaseUtil.getDBConnection(true);) {
            try (PreparedStatement prepStmt = connection.prepareStatement(sqlStmt)) {
                prepStmt.setString(1, IdleAccIdentificationConstants.LAST_LOGIN_TIME_CLAIM);
                prepStmt.setInt(2, tenantId);
                prepStmt.setString(3, inactiveDateEpoch);
                prepStmt.setString(4, excludeDateEpoch);
                try (ResultSet resultSet = prepStmt.executeQuery()) {
                    while (resultSet.next()) {
                        String username = resultSet.getString(1);
                        if (StringUtils.isNotBlank(username)) {
                            // inactiveUsers.add(username);
                            InactiveUserModel user = new InactiveUserModel();
                            user.setUsername(username);
                            user.setUserStoreDomain(UserCoreUtil.extractDomainFromName(username));
                            user.setEmail(fetchUserEmail(tenantId, realmService, username));
                            inactiveUsers.add(user);
                        }
                    }
                }
                IdentityDatabaseUtil.commitTransaction(connection);
                return inactiveUsers;
            }

        } catch (SQLException e) {
            throw new IdleAccIdentificationServerException(
                    IdleAccIdentificationConstants.ErrorMessages.ERROR_RETRIEVE_INACTIVE_USERS_FROM_DB.getCode(),
                    IdleAccIdentificationConstants.ErrorMessages.ERROR_RETRIEVE_INACTIVE_USERS_FROM_DB.getMessage());
        }
    }

    /**
     * Fetch email address attribute value of a user.
     *
     * @param tenantId      Tenant Id.
     * @param realmService  Realm Service.
     * @param username      username of the user.
     * @return              Email address of the user.
     */
    private String fetchUserEmail(int tenantId, RealmService realmService, String username) {

        String[] claims = new String[1];
        claims[0] = IdleAccIdentificationConstants.EMAIL_CLAIM;
        try {
            UserStoreManager userStoreManager = (UserStoreManager) realmService.getTenantUserRealm(tenantId).
                    getUserStoreManager();
            String userStoreDomain = userStoreManager.getRealmConfiguration().getUserStoreProperty(
                    UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME);

            if (StringUtils.isBlank(userStoreDomain)) {
                userStoreDomain = UserCoreConstants.PRIMARY_DEFAULT_DOMAIN_NAME;
            }

            if (userStoreDomain.equalsIgnoreCase(UserCoreUtil.extractDomainFromName(username))) {
                Map<String, String> map = userStoreManager.getUserClaimValues(username, claims, null);
                return map.get(IdleAccIdentificationConstants.EMAIL_CLAIM);
            }

        } catch (UserStoreException e) {
            throw new RuntimeException(e);
        }
        return username;
    }
}
