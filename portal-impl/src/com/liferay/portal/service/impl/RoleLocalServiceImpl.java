/**
 * Copyright (c) 2000-2007 Liferay, Inc. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.liferay.portal.service.impl;

import com.liferay.portal.DuplicateRoleException;
import com.liferay.portal.NoSuchRoleException;
import com.liferay.portal.PortalException;
import com.liferay.portal.RequiredRoleException;
import com.liferay.portal.RoleNameException;
import com.liferay.portal.SystemException;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.model.Group;
import com.liferay.portal.model.Role;
import com.liferay.portal.model.impl.GroupImpl;
import com.liferay.portal.model.impl.ResourceImpl;
import com.liferay.portal.model.impl.RoleImpl;
import com.liferay.portal.security.permission.PermissionCacheUtil;
import com.liferay.portal.security.permission.ResourceActionsUtil;
import com.liferay.portal.service.base.RoleLocalServiceBaseImpl;
import com.liferay.portal.util.PortalUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <a href="RoleLocalServiceImpl.java.html"><b><i>View Source</i></b></a>
 *
 * @author Brian Wing Shun Chan
 *
 */
public class RoleLocalServiceImpl extends RoleLocalServiceBaseImpl {

	public Role addRole(long userId, long companyId, String name, int type)
		throws PortalException, SystemException {

		return addRole(userId, companyId, name, type, null, 0);
	}

	public Role addRole(
			long userId, long companyId, String name, int type,
			String className, long classPK)
		throws PortalException, SystemException {

		// Role

		long classNameId = PortalUtil.getClassNameId(className);

		validate(0, companyId, name);

		long roleId = counterLocalService.increment();

		Role role = rolePersistence.create(roleId);

		role.setCompanyId(companyId);
		role.setClassNameId(classNameId);
		role.setClassPK(classPK);
		role.setName(name);
		role.setType(type);

		rolePersistence.update(role);

		// Resources

		if (userId > 0) {
			resourceLocalService.addResources(
				companyId, 0, userId, Role.class.getName(), role.getRoleId(),
				false, false, false);
		}

		return role;
	}

	public void checkSystemRoles(long companyId)
		throws PortalException, SystemException {

		// Regular roles

		String[] systemRoles = PortalUtil.getSystemRoles();

		for (int i = 0; i < systemRoles.length; i++) {
			try {
				roleFinder.findByC_N(companyId, systemRoles[i]);
			}
			catch (NoSuchRoleException nsre) {
				addRole(0, companyId, systemRoles[i], RoleImpl.TYPE_REGULAR);
			}
		}

		// Community roles

		String[] systemCommunityRoles = PortalUtil.getSystemCommunityRoles();

		for (int i = 0; i < systemCommunityRoles.length; i++) {
			try {
				roleFinder.findByC_N(companyId, systemCommunityRoles[i]);
			}
			catch (NoSuchRoleException nsre) {
				Role role = addRole(
					0, companyId, systemCommunityRoles[i],
					RoleImpl.TYPE_COMMUNITY);

				if (systemCommunityRoles[i].equals(RoleImpl.COMMUNITY_OWNER)) {
					List actions = ResourceActionsUtil.getModelResourceActions(
						Group.class.getName());

					permissionLocalService.setRolePermissions(
						role.getRoleId(), role.getCompanyId(),
						Group.class.getName(),
						ResourceImpl.SCOPE_GROUP_TEMPLATE,
						String.valueOf(GroupImpl.DEFAULT_PARENT_GROUP_ID),
						(String[])actions.toArray(new String[0]));
				}
				else if (systemCommunityRoles[i].equals(
							RoleImpl.COMMUNITY_ADMINISTRATOR)) {

					String[] actionIds = new String[] {
						ActionKeys.ASSIGN_USERS, ActionKeys.MANAGE_LAYOUTS,
						ActionKeys.UPDATE
					};

					permissionLocalService.setRolePermissions(
						role.getRoleId(), role.getCompanyId(),
						Group.class.getName(),
						ResourceImpl.SCOPE_GROUP_TEMPLATE,
						String.valueOf(GroupImpl.DEFAULT_PARENT_GROUP_ID),
						actionIds);
				}
			}
		}

		// Organization roles

		String[] systemOrganizationRoles =
			PortalUtil.getSystemOrganizationRoles();

		for (int i = 0; i < systemOrganizationRoles.length; i++) {
			try {
				roleFinder.findByC_N(companyId, systemOrganizationRoles[i]);
			}
			catch (NoSuchRoleException nsre) {
				Role role = addRole(
					0, companyId, systemOrganizationRoles[i],
					RoleImpl.TYPE_ORGANIZATION);

				if (systemOrganizationRoles[i].equals(
						RoleImpl.ORGANIZATION_ADMINISTRATOR)) {

					List actions = ResourceActionsUtil.getModelResourceActions(
						Group.class.getName());

					permissionLocalService.setRolePermissions(
						role.getRoleId(), role.getCompanyId(),
						Group.class.getName(),
						ResourceImpl.SCOPE_GROUP_TEMPLATE,
						String.valueOf(GroupImpl.DEFAULT_PARENT_GROUP_ID),
						(String[])actions.toArray(new String[0]));
				}
			}
		}
	}

	public void deleteRole(long roleId)
		throws PortalException, SystemException {

		Role role = rolePersistence.findByPrimaryKey(roleId);

		if (PortalUtil.isSystemRole(role.getName())) {
			throw new RequiredRoleException();
		}

		// Resources

		if ((role.getClassNameId() <= 0) && (role.getClassPK() <= 0)) {
			resourceLocalService.deleteResource(
				role.getCompanyId(), Role.class.getName(),
				ResourceImpl.SCOPE_INDIVIDUAL, role.getRoleId());
		}

		if ((role.getType() == RoleImpl.TYPE_COMMUNITY) ||
			(role.getType() == RoleImpl.TYPE_ORGANIZATION)) {

			userGroupRoleLocalService.deleteUserGroupRolesByRoleId(
				role.getRoleId());
		}

		// Role

		rolePersistence.remove(roleId);

		// Permission cache

		PermissionCacheUtil.clearCache();
	}

	public Role getGroupRole(long companyId, long groupId)
		throws PortalException, SystemException {

		long classNameId = PortalUtil.getClassNameId(Group.class);

		return rolePersistence.findByC_C_C(companyId, classNameId, groupId);
	}

	public List getGroupRoles(long groupId)
		throws PortalException, SystemException {

		return groupPersistence.getRoles(groupId);
	}

	public Map getResourceRoles(
			long companyId, String name, int scope, String primKey)
		throws SystemException {

		return roleFinder.findByC_N_S_P(companyId, name, scope, primKey);
	}

	public Role getRole(long roleId) throws PortalException, SystemException {
		return rolePersistence.findByPrimaryKey(roleId);
	}

	public Role getRole(long companyId, String name)
		throws PortalException, SystemException {

		return roleFinder.findByC_N(companyId, name);
	}

	public List getUserGroupRoles(long userId, long groupId)
		throws SystemException {

		return roleFinder.findByUserGroupRole(userId, groupId);
	}

	public List getUserRelatedRoles(long userId, long groupId)
		throws SystemException {

		return roleFinder.findByU_G(userId, groupId);
	}

	public List getUserRelatedRoles(long userId, long[] groupIds)
		throws SystemException {

		return roleFinder.findByU_G(userId, groupIds);
	}

	public List getUserRelatedRoles(long userId, List groups)
		throws SystemException {

		return roleFinder.findByU_G(userId, groups);
	}

	public List getUserRoles(long userId)
		throws PortalException, SystemException {

		return userPersistence.getRoles(userId);
	}

	/**
	 * Returns true if the user has the role.
	 *
	 * @param		userId the user id of the user
	 * @param		companyId the company id of the company
	 * @param		name the name of the role
	 * @param		inherited boolean value for whether to check roles inherited
	 *				from the community, organization, location, or user group
	 * @return		true if the user has the role
	 */
	public boolean hasUserRole(
			long userId, long companyId, String name, boolean inherited)
		throws PortalException, SystemException {

		Role role = roleFinder.findByC_N(companyId, name);

		if (inherited) {
			if (roleFinder.countByR_U(role.getRoleId(), userId) > 0) {
				return true;
			}
			else {
				return false;
			}
		}
		else {
			return userPersistence.containsRole(userId, role.getRoleId());
		}
	}

	/**
	 * Returns true if the user has any one of the specified roles.
	 *
	 * @param		userId the user id of the user
	 * @param		companyId the company id of the company
	 * @param		names an array of role names
	 * @param		inherited boolean value for whether to check roles inherited
	 *				from the community, organization, location, or user group
	 * @return		true if the user has the role
	 */
	public boolean hasUserRoles(
			long userId, long companyId, String[] names, boolean inherited)
		throws PortalException, SystemException {

		for (int i = 0; i < names.length; i++) {
			if (hasUserRole(userId, companyId, names[i], inherited)) {
				return true;
			}
		}

		return false;
	}

	public List search(
			long companyId, String name, String description, Integer type,
			int begin, int end)
		throws SystemException {

		return search (
			companyId, name, description, type, new LinkedHashMap(), begin,
			end);
	}

	public List search(
			long companyId, String name, String description, Integer type,
			LinkedHashMap params, int begin, int end)
		throws SystemException {

		return roleFinder.findByC_N_D_T(
			companyId, name, description, type, params, begin, end);
	}

	public int searchCount(
			long companyId, String name, String description, Integer type)
		throws SystemException {

		return searchCount(
			companyId, name, description, type, new LinkedHashMap());
	}

	public int searchCount(
			long companyId, String name, String description, Integer type,
			LinkedHashMap params)
		throws SystemException {

		return roleFinder.countByC_N_D_T(
			companyId, name, description, type, params);
	}

	public void setUserRoles(long userId, long[] roleIds)
		throws PortalException, SystemException {

		userPersistence.setRoles(userId, roleIds);

		PermissionCacheUtil.clearCache();
	}

	public Role updateRole(long roleId, String name)
		throws PortalException, SystemException {

		Role role = rolePersistence.findByPrimaryKey(roleId);

		validate(roleId, role.getCompanyId(), name);

		if (PortalUtil.isSystemRole(role.getName())) {
			throw new RequiredRoleException();
		}

		role.setName(name);

		rolePersistence.update(role);

		return role;
	}

	protected void validate(long roleId, long companyId, String name)
		throws PortalException, SystemException {

		if ((Validator.isNull(name)) || (Validator.isNumber(name)) ||
			(name.indexOf(StringPool.COMMA) != -1) ||
			(name.indexOf(StringPool.STAR) != -1)) {

			throw new RoleNameException();
		}

		try {
			Role role = roleFinder.findByC_N(companyId, name);

			if (role.getRoleId() != roleId) {
				throw new DuplicateRoleException();
			}
		}
		catch (NoSuchRoleException nsge) {
		}
	}

}