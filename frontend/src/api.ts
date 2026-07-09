// Thin client over the Membership REST surface (see docs/contexts/membership/spec.md §6).

export const WELLNESS_GOALS = [
  "SLEEP_BETTER",
  "MOVE_MORE",
  "EAT_WELL",
  "STRESS_LESS",
  "BUILD_STRENGTH",
] as const;

export type WellnessGoal = (typeof WELLNESS_GOALS)[number];

export interface Member {
  id: string;
  email: string;
  fullName: string;
  status: "REGISTERED" | "ONBOARDED";
  goals: WellnessGoal[];
  dateOfBirth: string | null;
  registeredAt: string;
  onboardedAt: string | null;
}

async function parseError(res: Response): Promise<never> {
  // The backend returns RFC 7807 ProblemDetail on domain errors.
  const problem = await res.json().catch(() => null);
  throw new Error(problem?.detail ?? `Request failed (${res.status})`);
}

export async function registerMember(email: string, fullName: string): Promise<string> {
  const res = await fetch("/api/members", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, fullName }),
  });
  if (!res.ok) return parseError(res);
  const body = (await res.json()) as { id: string };
  return body.id;
}

export async function completeOnboarding(
  memberId: string,
  goals: WellnessGoal[],
  dateOfBirth: string | null
): Promise<Member> {
  const res = await fetch(`/api/members/${memberId}/onboarding`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ goals, dateOfBirth: dateOfBirth || null }),
  });
  if (!res.ok) return parseError(res);
  return (await res.json()) as Member;
}

export async function getMember(memberId: string): Promise<Member> {
  const res = await fetch(`/api/members/${memberId}`);
  if (!res.ok) return parseError(res);
  return (await res.json()) as Member;
}
