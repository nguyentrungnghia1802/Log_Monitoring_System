import type {
  InviteOrganizationMemberRequest,
  OrganizationMember,
  OrganizationSummary,
  UpdateOrganizationMemberRequest,
} from "../types/organization";
import { apiRequest } from "./http";

export function fetchCurrentOrganization(): Promise<OrganizationSummary> {
  return apiRequest<OrganizationSummary>("/organizations/current");
}

export function fetchOrganizationMembers(): Promise<OrganizationMember[]> {
  return apiRequest<OrganizationMember[]>("/organizations/current/users");
}

export function updateCurrentOrganization(
  name: string,
  settings: Record<string, string>,
) {
  return apiRequest<OrganizationSummary>("/organizations/current", {
    method: "PATCH",
    body: JSON.stringify({ name, settings }),
  });
}

export function inviteOrganizationMember(
  requestBody: InviteOrganizationMemberRequest,
) {
  return apiRequest<OrganizationMember>("/organizations/current/users", {
    method: "POST",
    body: JSON.stringify(requestBody),
  });
}

export function updateOrganizationMember(
  userId: string,
  requestBody: UpdateOrganizationMemberRequest,
) {
  return apiRequest<OrganizationMember>(
    `/organizations/current/users/${userId}`,
    {
      method: "PATCH",
      body: JSON.stringify(requestBody),
    },
  );
}

export function removeOrganizationMember(userId: string) {
  return apiRequest<void>(`/organizations/current/users/${userId}`, {
    method: "DELETE",
  });
}
