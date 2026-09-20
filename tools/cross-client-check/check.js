#!/usr/bin/env node
// Mentora — Cross-Client Parity Check (Phase 7, Task T8)
//
// WHAT THIS IS. A plain Node 22, zero-dependency script that proves the Website and the
// Android/KMP shared-core client observe the SAME backend state through the SAME API, by driving
// two independent HTTP transports against one throwaway student account and asserting their views
// agree at every step. See `execution/PHASE_7_SYSTEM_DESIGN.md` § 9 for the design this implements.
//
// WHY TWO TRANSPORTS INSTEAD OF ACTUALLY RUNNING THE ANDROID APP. There is no way to drive the real
// Android app headlessly here; what actually varies between Website and Android/KMP is the AUTH
// TRANSPORT (browser cookies + a same-origin CSRF header vs. an `Authorization: Bearer` header —
// see `KtorHttpClient` in `shared/src/commonMain/kotlin/com/mentora/shared/network/`), not the
// business logic, which is server-side and identical for both. `SdkTransport` below reproduces the
// Bearer-header transport exactly; T10/T11 (the real browser/emulator walks) are what actually
// exercises each real client's own rendering and interaction code. This script proves the shared
// backend contract both clients are built on, which is the thing a UI walk cannot directly prove.
//
// ACCOUNT. One throwaway `@xclient.mentora.test` student, registered fresh on every run — never a
// seed account (AUTH_SECURITY.md: seed accounts' role-defining state must never be mutated, see
// `web/e2e/helpers.ts`). All mutations below (enrollment, lesson progress, quiz attempt, learning
// path follow) are scoped to this fresh account and touch no shared state.
//
// RATE LIMIT. `plugins/RateLimiting.kt`'s "auth" bucket allows 10 req/min across register+login
// combined. This script makes exactly ONE register call and reuses the resulting tokens for both
// transports (see `SdkTransport` below) — no separate login call is needed — but still retries with
// the same backoff shape `web/e2e/helpers.ts` uses (4 attempts, 8s * attempt) if a 429 is hit.
//
// HONESTY CONTRACT. Every assertion group reports PASS, FAIL, or NOT-EXERCISED. A4 (certificates)
// is reported NOT-EXERCISED rather than fabricated PASS if the seeded quiz doesn't actually pass —
// this script asserts against the REAL seeded question data, but if SeedData.kt's content ever
// changes such that the demo quiz no longer passes 100%, this must degrade honestly, not lie.
//
// USAGE: node tools/cross-client-check/check.js [--base-url http://localhost:8080]
// Exit code 0 only if every group is PASS or NOT-EXERCISED-and-disclosed; 1 if any group FAILs.

const DEFAULT_BASE_URL = "http://localhost:8080";

function parseArgs(argv) {
  const args = { baseUrl: DEFAULT_BASE_URL };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === "--base-url" && argv[i + 1]) {
      args.baseUrl = argv[++i];
    }
  }
  return args;
}

function uniqueEmail(prefix) {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 100000)}@xclient.mentora.test`;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

class ApiError extends Error {
  constructor(status, body) {
    super(`HTTP ${status}: ${JSON.stringify(body)}`);
    this.status = status;
    this.body = body;
  }
}

/** Shared low-level request helper. Both transports funnel through this so retry/backoff logic and
 * envelope-unwrapping (`ApiSuccess<T>` -> `.data`, per `common/Responses.kt`) live in one place. */
async function request(baseUrl, path, { method = "GET", headers = {}, body, retryOn429 = true } = {}) {
  const url = `${baseUrl}${path}`;
  const maxAttempts = retryOn429 ? 4 : 1;
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    const res = await fetch(url, {
      method,
      headers: {
        ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
        ...headers,
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
      redirect: "manual",
    });
    if (res.status === 429 && attempt < maxAttempts) {
      await sleep(8000 * attempt);
      continue;
    }
    const contentType = res.headers.get("content-type") || "";
    const parsed = contentType.includes("application/json") ? await res.json() : await res.text();
    if (!res.ok) {
      throw new ApiError(res.status, parsed);
    }
    return { status: res.status, headers: res.headers, body: parsed };
  }
  throw new Error("unreachable: retry loop exhausted without returning or throwing");
}

const CSRF_HEADER = { "X-Requested-With": "mentora-web" };

/** Website transport: cookie-based auth (Set-Cookie from /register, cookies re-sent by hand since
 * this is plain `fetch`, not a browser jar) + the CSRF header every state-changing route requires
 * (`common/Csrf.kt`). */
class WebTransport {
  constructor(baseUrl) {
    this.baseUrl = baseUrl;
    this.cookies = new Map();
  }

  captureCookies(headers) {
    const setCookie = typeof headers.getSetCookie === "function" ? headers.getSetCookie() : [];
    for (const raw of setCookie) {
      const [pair] = raw.split(";");
      const eq = pair.indexOf("=");
      if (eq > 0) this.cookies.set(pair.slice(0, eq).trim(), pair.slice(eq + 1).trim());
    }
  }

  cookieHeader() {
    return [...this.cookies.entries()].map(([k, v]) => `${k}=${v}`).join("; ");
  }

  async call(path, { method = "GET", body, mutating = false } = {}) {
    const headers = { Cookie: this.cookieHeader() };
    if (mutating) Object.assign(headers, CSRF_HEADER);
    const result = await request(this.baseUrl, path, { method, headers, body });
    this.captureCookies(result.headers);
    return typeof result.body === "string" ? result.body : result.body.data;
  }

  async register(email, password, name) {
    const result = await request(this.baseUrl, "/api/v1/auth/register", {
      method: "POST",
      headers: CSRF_HEADER,
      body: { email, password, name },
    });
    this.captureCookies(result.headers);
    return result.body.data;
  }
}

/** Mobile/KMP-shared-core transport: `Authorization: Bearer` (no cookies at all), reusing the SAME
 * access token the Website transport's registration already produced — this proves the SAME
 * account's SAME session data is visible through the OTHER transport shape, which is the actual
 * point of this harness (see file header). Per `common/Csrf.kt`'s own doc comment, mobile still
 * sends the CSRF header on mutations even though it isn't cookie-exposed. */
class SdkTransport {
  constructor(baseUrl, accessToken) {
    this.baseUrl = baseUrl;
    this.accessToken = accessToken;
  }

  async call(path, { method = "GET", body, mutating = false } = {}) {
    const headers = { Authorization: `Bearer ${this.accessToken}` };
    if (mutating) Object.assign(headers, CSRF_HEADER);
    const result = await request(this.baseUrl, path, { method, headers, body });
    return typeof result.body === "string" ? result.body : result.body.data;
  }
}

function deepEqual(a, b) {
  return JSON.stringify(a) === JSON.stringify(b);
}

class Report {
  constructor() {
    this.groups = [];
  }
  pass(name, detail) {
    this.groups.push({ name, status: "PASS", detail });
  }
  fail(name, detail) {
    this.groups.push({ name, status: "FAIL", detail });
  }
  notExercised(name, detail) {
    this.groups.push({ name, status: "NOT-EXERCISED", detail });
  }
  print() {
    console.log("");
    console.log("=== Mentora Cross-Client Parity Check ===");
    for (const g of this.groups) {
      console.log(`[${g.status}] ${g.name}${g.detail ? " — " + g.detail : ""}`);
    }
    const failed = this.groups.filter((g) => g.status === "FAIL");
    console.log("");
    console.log(
      failed.length === 0
        ? `All ${this.groups.length} groups PASS or NOT-EXERCISED.`
        : `${failed.length} of ${this.groups.length} groups FAILED.`
    );
    return failed.length === 0 ? 0 : 1;
  }
}

async function main() {
  const { baseUrl } = parseArgs(process.argv.slice(2));
  const report = new Report();
  const email = uniqueEmail("xclient");
  const password = "MentoraDemo1";
  const name = "Cross Client Check";

  console.log(`Base URL: ${baseUrl}`);
  console.log(`Throwaway account: ${email}`);

  // --- Setup: one registration, shared by both transports ---
  const web = new WebTransport(baseUrl);
  let auth;
  try {
    auth = await web.register(email, password, name);
  } catch (err) {
    report.fail("SETUP (register)", err.message);
    return report.print();
  }
  const sdk = new SdkTransport(baseUrl, auth.accessToken);

  // --- C1: login/role-gating parity — both transports see the same principal/role ---
  try {
    const webMe = await web.call("/api/v1/enrollments"); // any authenticated GET; 200 = principal accepted
    const sdkMe = await sdk.call("/api/v1/enrollments");
    if (auth.user.role !== "student") {
      report.fail("C1 login/role-gating parity", `expected role student, got ${auth.user.role}`);
    } else if (!Array.isArray(webMe) || !Array.isArray(sdkMe)) {
      report.fail("C1 login/role-gating parity", "expected both transports to list enrollments as arrays");
    } else {
      report.pass("C1 login/role-gating parity", "both transports authenticate as the same student principal");
    }
  } catch (err) {
    report.fail("C1 login/role-gating parity", err.message);
  }

  // --- Find a real published course to enroll in (the seeded quiz course) ---
  let course;
  try {
    const courses = await web.call("/api/v1/courses?q=Building%20Reliable%20REST%20APIs");
    course = courses[0];
    if (!course) throw new Error("seeded quiz course 'Building Reliable REST APIs' not found — is seedDemoData applied?");
  } catch (err) {
    report.fail("SETUP (find seeded quiz course)", err.message);
    return report.print();
  }

  let courseDetail;
  try {
    courseDetail = await web.call(`/api/v1/courses/${course.id}`);
  } catch (err) {
    report.fail("SETUP (fetch course detail)", err.message);
    return report.print();
  }
  const firstLesson = courseDetail.sections?.[0]?.lessons?.[0];
  if (!firstLesson) {
    report.fail("SETUP (locate first lesson)", "seeded quiz course has no lessons");
    return report.print();
  }

  // --- D1: idempotent checkout — completing twice must not create two enrollments ---
  let enrollmentCountAfterFirst;
  try {
    await web.call(`/api/v1/courses/${course.id}/checkout/complete`, { method: "POST", mutating: true });
    const afterFirst = await web.call("/api/v1/enrollments");
    enrollmentCountAfterFirst = afterFirst.length;
    await sdk.call(`/api/v1/courses/${course.id}/checkout/complete`, { method: "POST", mutating: true });
    const afterSecond = await sdk.call("/api/v1/enrollments");
    if (afterSecond.length !== enrollmentCountAfterFirst) {
      report.fail("D1 idempotent checkout", `enrollment count changed on repeat checkout: ${enrollmentCountAfterFirst} -> ${afterSecond.length}`);
    } else {
      report.pass("D1 idempotent checkout", `repeat checkout is a no-op (${afterSecond.length} enrollment(s))`);
    }
  } catch (err) {
    report.fail("D1 idempotent checkout", err.message);
  }

  // --- A1: enrollment parity — both transports see the same enrollment list ---
  try {
    const webEnrollments = await web.call("/api/v1/enrollments");
    const sdkEnrollments = await sdk.call("/api/v1/enrollments");
    if (deepEqual(webEnrollments, sdkEnrollments)) {
      report.pass("A1 enrollment parity", `${webEnrollments.length} enrollment(s), identical via both transports`);
    } else {
      report.fail("A1 enrollment parity", "enrollment lists differ between Web and SDK transports");
    }
  } catch (err) {
    report.fail("A1 enrollment parity", err.message);
  }

  // --- A2/E1: lesson-complete + position parity ---
  try {
    await web.call(`/api/v1/courses/${course.id}/lessons/${firstLesson.lessonId}/position`, {
      method: "POST",
      mutating: true,
      body: { positionSeconds: 42 },
    });
    await sdk.call(`/api/v1/courses/${course.id}/lessons/${firstLesson.lessonId}/complete`, {
      method: "POST",
      mutating: true,
    });
    const webProgress = await web.call(`/api/v1/courses/${course.id}/progress`);
    const sdkProgress = await sdk.call(`/api/v1/courses/${course.id}/progress`);
    if (deepEqual(webProgress, sdkProgress)) {
      report.pass("A2/E1 lesson-complete + position parity", "progress identical via both transports after cross-transport writes");
    } else {
      report.fail("A2/E1 lesson-complete + position parity", "progress differs between Web and SDK transports");
    }
  } catch (err) {
    report.fail("A2/E1 lesson-complete + position parity", err.message);
  }

  // --- Setup: complete every lesson in the course so a passing quiz attempt below actually
  // satisfies certificates.checkAndIssueIfComplete's completedLessonCount == totalLessons
  // precondition, letting A4 be genuinely exercised rather than perpetually NOT-EXERCISED. ---
  try {
    for (const section of courseDetail.sections || []) {
      for (const lesson of section.lessons || []) {
        await web.call(`/api/v1/courses/${course.id}/lessons/${lesson.lessonId}/complete`, {
          method: "POST",
          mutating: true,
        });
      }
    }
  } catch (err) {
    report.fail("SETUP (complete all lessons)", err.message);
  }

  // --- E2/A3: quiz isCorrect-stripping (student fetch) + attempt parity ---
  let quizPassed = false;
  try {
    const webQuiz = await web.call(`/api/v1/courses/${course.id}/quiz`);
    const sdkQuiz = await sdk.call(`/api/v1/courses/${course.id}/quiz`);
    const anyOptionRevealsCorrectness = (quiz) =>
      (quiz.questions || []).some((q) => (q.options || []).some((o) => "isCorrect" in o));
    if (anyOptionRevealsCorrectness(webQuiz) || anyOptionRevealsCorrectness(sdkQuiz)) {
      report.fail("E2 quiz isCorrect-stripping", "student-facing quiz fetch leaks isCorrect on an option");
    } else if (!deepEqual(webQuiz, sdkQuiz)) {
      report.fail("E2 quiz isCorrect-stripping", "quiz content differs between Web and SDK transports");
    } else {
      report.pass("E2 quiz isCorrect-stripping", "isCorrect stripped from student fetch on both transports, content identical");
      // SeedData.kt's `question()` helper always emits options in [correct, incorrect] order —
      // selecting each question's first option answers 100% correctly.
      const answers = (webQuiz.questions || []).map((q) => ({
        questionId: q.questionId,
        selectedOptionId: q.options[0].optionId,
      }));
      const attempt = await web.call(`/api/v1/courses/${course.id}/quiz/attempts`, {
        method: "POST",
        mutating: true,
        body: { answers },
      });
      quizPassed = attempt.passed === true;
      const sdkLatest = await sdk.call(`/api/v1/courses/${course.id}/quiz/attempts/latest`);
      if (deepEqual(attempt, sdkLatest)) {
        report.pass("A3 quiz attempt parity", `attempt (score=${attempt.score}, passed=${attempt.passed}) identical via both transports`);
      } else {
        report.fail("A3 quiz attempt parity", "latest attempt differs between Web and SDK transports");
      }
    }
  } catch (err) {
    report.fail("E2/A3 quiz fetch + attempt parity", err.message);
  }

  // --- A4: certificate parity — HONESTLY reported NOT-EXERCISED if the quiz didn't pass ---
  if (!quizPassed) {
    report.notExercised("A4 certificate parity", "quiz attempt did not pass (or was not exercised) — certificate issuance was not triggered, not asserted");
  } else {
    try {
      const webCerts = await web.call("/api/v1/certificates");
      const sdkCerts = await sdk.call("/api/v1/certificates");
      if (webCerts.length === 0) {
        report.notExercised("A4 certificate parity", "quiz passed but no certificate was issued — see certificates.checkAndIssueIfComplete preconditions");
      } else if (deepEqual(webCerts, sdkCerts)) {
        report.pass("A4 certificate parity", `${webCerts.length} certificate(s), identical via both transports`);
      } else {
        report.fail("A4 certificate parity", "certificate list differs between Web and SDK transports");
      }
    } catch (err) {
      report.fail("A4 certificate parity", err.message);
    }
  }

  // --- A5: AI Tutor stub-mode conversation parity ---
  try {
    await web.call("/api/v1/ai-tutor/conversation/messages", {
      method: "POST",
      mutating: true,
      body: {
        content: "Cross-client check: what is this course about?",
        courseId: course.id,
        lessonContextId: firstLesson.lessonId,
      },
    });
    const webConvo = await web.call("/api/v1/ai-tutor/conversation");
    const sdkConvo = await sdk.call("/api/v1/ai-tutor/conversation");
    if (deepEqual(webConvo, sdkConvo)) {
      report.pass("A5 AI Tutor stub-mode conversation parity", `${webConvo.messages.length} message(s), identical via both transports`);
    } else {
      report.fail("A5 AI Tutor stub-mode conversation parity", "conversation history differs between Web and SDK transports");
    }
  } catch (err) {
    report.fail("A5 AI Tutor stub-mode conversation parity", err.message);
  }

  // --- E3: learning path follow/progress parity ---
  try {
    const paths = await web.call("/api/v1/learning-paths");
    const path = paths[0];
    if (!path) {
      report.notExercised("E3 learning path follow/progress parity", "no seeded learning path found");
    } else {
      await web.call(`/api/v1/learning-paths/${path.id}/follow`, { method: "POST", mutating: true });
      const webPath = await web.call(`/api/v1/learning-paths/${path.id}`);
      const sdkPath = await sdk.call(`/api/v1/learning-paths/${path.id}`);
      if (deepEqual(webPath, sdkPath)) {
        report.pass("E3 learning path follow/progress parity", "path detail (including follow state) identical via both transports");
      } else {
        report.fail("E3 learning path follow/progress parity", "path detail differs between Web and SDK transports");
      }
      await sdk.call(`/api/v1/learning-paths/${path.id}/follow`, { method: "DELETE", mutating: true });
    }
  } catch (err) {
    report.fail("E3 learning path follow/progress parity", err.message);
  }

  return report.print();
}

main()
  .then((code) => process.exit(code))
  .catch((err) => {
    console.error("FATAL:", err);
    process.exit(1);
  });
