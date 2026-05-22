import { GroupResponse, RoleResponse, UserResponse } from '../types/api';

export function roleNames(user: UserResponse, roles: RoleResponse[] = []) {
  return user.roleIds
    .map((roleId) => roles.find((role) => role.id === roleId)?.name ?? roleId)
    .join(', ');
}

export function userGroups(userId: string, groups: GroupResponse[] = []) {
  return groups.filter((group) => group.memberIds.includes(userId));
}

export function displayUser(user: UserResponse) {
  return `${user.displayName} (${user.username})`;
}
