import { useState } from "react";
import {
  WELLNESS_GOALS,
  WellnessGoal,
  Member,
  registerMember,
  completeOnboarding,
} from "./api";

type Step = "register" | "onboard" | "done";

export function App() {
  const [step, setStep] = useState<Step>("register");
  const [memberId, setMemberId] = useState<string>("");
  const [member, setMember] = useState<Member | null>(null);
  const [error, setError] = useState<string>("");

  // register form
  const [email, setEmail] = useState("");
  const [fullName, setFullName] = useState("");

  // onboarding form
  const [goals, setGoals] = useState<WellnessGoal[]>([]);
  const [dob, setDob] = useState("");

  async function onRegister(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    try {
      const id = await registerMember(email, fullName);
      setMemberId(id);
      setStep("onboard");
    } catch (err) {
      setError((err as Error).message);
    }
  }

  function toggleGoal(goal: WellnessGoal) {
    setGoals((prev) =>
      prev.includes(goal) ? prev.filter((g) => g !== goal) : [...prev, goal]
    );
  }

  async function onOnboard(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    try {
      const m = await completeOnboarding(memberId, goals, dob || null);
      setMember(m);
      setStep("done");
    } catch (err) {
      setError((err as Error).message);
    }
  }

  return (
    <main className="card">
      <h1>WellConverge</h1>
      <p className="subtitle">Membership · Iteration 1</p>

      {error && <p className="error">{error}</p>}

      {step === "register" && (
        <form onSubmit={onRegister}>
          <h2>Create your account</h2>
          <label>
            Email
            <input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" />
          </label>
          <label>
            Full name
            <input value={fullName} onChange={(e) => setFullName(e.target.value)} placeholder="Ada Lovelace" />
          </label>
          <button type="submit">Register</button>
        </form>
      )}

      {step === "onboard" && (
        <form onSubmit={onOnboard}>
          <h2>What are your wellness goals?</h2>
          <p className="hint">Pick at least one.</p>
          <div className="goals">
            {WELLNESS_GOALS.map((goal) => (
              <label key={goal} className={goals.includes(goal) ? "goal selected" : "goal"}>
                <input
                  type="checkbox"
                  checked={goals.includes(goal)}
                  onChange={() => toggleGoal(goal)}
                />
                {goal.replace(/_/g, " ").toLowerCase()}
              </label>
            ))}
          </div>
          <label>
            Date of birth (optional)
            <input type="date" value={dob} onChange={(e) => setDob(e.target.value)} />
          </label>
          <button type="submit">Complete onboarding</button>
        </form>
      )}

      {step === "done" && member && (
        <section>
          <h2>You're all set, {member.fullName.split(" ")[0]} 🎉</h2>
          <p>
            Status: <strong>{member.status}</strong>
          </p>
          <p>Goals:</p>
          <ul>
            {member.goals.map((g) => (
              <li key={g}>{g.replace(/_/g, " ").toLowerCase()}</li>
            ))}
          </ul>
          <pre className="debug">{JSON.stringify(member, null, 2)}</pre>
        </section>
      )}
    </main>
  );
}
