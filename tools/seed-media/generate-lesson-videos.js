#!/usr/bin/env node
/**
 * Generates one small, unique, topic-referencing DEMO video per seeded lesson (48 total, across
 * the 4 published demo courses) via ffmpeg — same proven technique as the course-level videos
 * committed for D59/D60 (offline drawtext/drawbox/fade slideshow, no stock footage, no network
 * dependency at runtime), just one per lesson instead of one shared per course.
 *
 * Requires: ffmpeg + ffprobe on PATH (installed via `winget install Gyan.FFmpeg` per D59).
 * Output: backend/src/main/resources/seed-media/lessons/<course-slug>/<NN>-<lesson-slug>.mp4
 * Also writes generated-lesson-videos.json (slug -> real ffprobe duration in seconds) so
 * SeedData.kt's per-lesson durationSeconds can be the file's own true duration, never invented.
 *
 * Run from the repo root: `node tools/seed-media/generate-lesson-videos.js`
 * Not part of the runtime app or the seedDemoData task — a one-time/rerunnable asset generator,
 * same role as tools/token-pipeline and tools/design-to-code (plain Node, no new dependency).
 */
"use strict";

const fs = require("fs");
const os = require("os");
const path = require("path");
const { execFileSync } = require("child_process");

const REPO_ROOT = path.resolve(__dirname, "..", "..");
const OUT_ROOT = path.join(REPO_ROOT, "backend", "src", "main", "resources", "seed-media", "lessons");
const WORK_DIR = fs.mkdtempSync(path.join(os.tmpdir(), "mentora-lesson-video-"));

const FONT_DIR = path.join(WORK_DIR, "fonts");
fs.mkdirSync(FONT_DIR, { recursive: true });
for (const font of ["arial.ttf", "arialbd.ttf", "tahoma.ttf", "tahomabd.ttf"]) {
  fs.copyFileSync(path.join("C:", "Windows", "Fonts", font), path.join(FONT_DIR, font));
}

const WIDTH = 640;
const HEIGHT = 360;
const FPS = 20;

/** Greedy word-wrap so drawtext (which never auto-wraps) fits inside the 640px frame. */
function wrap(text, maxCharsPerLine) {
  const words = text.split(" ");
  const lines = [];
  let line = "";
  for (const word of words) {
    const candidate = line ? `${line} ${word}` : word;
    if (candidate.length > maxCharsPerLine && line) {
      lines.push(line);
      line = word;
    } else {
      line = candidate;
    }
  }
  if (line) lines.push(line);
  return lines.join("\n");
}

function writeTextFile(name, content) {
  const filePath = path.join(WORK_DIR, name);
  fs.writeFileSync(filePath, content, "utf8");
  return name; // relative to WORK_DIR, used as ffmpeg cwd, avoids Windows drive-colon escaping
}

function slugCourseDir(courseSlug) {
  const dir = path.join(OUT_ROOT, courseSlug);
  fs.mkdirSync(dir, { recursive: true });
  return dir;
}

const COURSES = [
  {
    slug: "rest-api",
    label: "BUILDING RELIABLE REST APIs",
    color: "0x1D4ED8",
    accent: "0x93C5FD",
    arabic: false,
    lessons: [
      { slug: "01-rest-api-reliability-fundamentals", title: "REST API Reliability Fundamentals", keywords: ["RESOURCES", "HTTP SEMANTICS", "VALIDATION", "STATUS CODES"] },
      { slug: "02-resources-and-http-methods", title: "Resources and HTTP Methods", keywords: ["GET", "POST", "PUT", "DELETE"] },
      { slug: "03-request-and-response-design", title: "Request and Response Design", keywords: ["JSON PAYLOAD", "HEADERS", "CONSISTENCY", "ENDPOINTS"] },
      { slug: "04-http-status-codes", title: "HTTP Status Codes", keywords: ["2xx SUCCESS", "4xx CLIENT ERROR", "5xx SERVER ERROR", "PRECISION"] },
      { slug: "05-request-validation", title: "Request Validation", keywords: ["INPUT CHECKS", "BOUNDARY", "REJECT EARLY", "CLEAN DATA"] },
      { slug: "06-consistent-error-responses", title: "Consistent Error Responses", keywords: ["ONE ERROR SHAPE", "CODE PATH", "CLIENT HANDLING", "CLARITY"] },
      { slug: "07-api-contracts-and-dtos", title: "API Contracts and DTOs", keywords: ["PUBLIC CONTRACT", "DTO", "DOMAIN MODEL", "DECOUPLING"] },
      { slug: "08-idempotency", title: "Idempotency", keywords: ["RETRY SAFE", "SAME RESULT", "NO DUPLICATES", "RELIABILITY"] },
      { slug: "09-pagination-and-filtering", title: "Pagination and Filtering", keywords: ["PAGES", "LIMIT", "CURSOR", "FILTER RESULTS"] },
      { slug: "10-authentication-and-authorization-concepts", title: "Authentication and Authorization Concepts", keywords: ["WHO ARE YOU", "WHAT CAN YOU DO", "AUTHN", "AUTHZ"] },
      { slug: "11-versioning-and-api-evolution", title: "Versioning and API Evolution", keywords: ["V1", "V2", "BACKWARD COMPAT", "EVOLUTION"] },
      { slug: "12-reliability-best-practices", title: "Reliability Best Practices", keywords: ["VALIDATE", "HANDLE ERRORS", "IDEMPOTENT", "SHIP CONFIDENTLY"] },
    ],
  },
  {
    slug: "mongodb",
    label: "PRACTICAL MONGODB FOR APPLICATION DEVELOPERS",
    color: "0x047857",
    accent: "0x6EE7B7",
    arabic: false,
    lessons: [
      { slug: "01-mongodb-fundamentals-for-application-developers", title: "MongoDB Fundamentals for Application Developers", keywords: ["DOCUMENTS", "CRUD", "SCHEMA", "INDEXES"] },
      { slug: "02-documents-and-collections", title: "Documents and Collections", keywords: ["JSON-LIKE", "COLLECTION", "FLEXIBLE", "NO TABLES"] },
      { slug: "03-mongodb-data-types", title: "MongoDB Data Types", keywords: ["OBJECTID", "DATES", "ARRAYS", "EMBEDDED DOCS"] },
      { slug: "04-crud-fundamentals", title: "CRUD Fundamentals", keywords: ["CREATE", "READ", "UPDATE", "DELETE"] },
      { slug: "05-query-operators", title: "Query Operators", keywords: ["$EQ", "$GT", "$IN", "$AND"] },
      { slug: "06-schema-design", title: "Schema Design", keywords: ["READ PATTERNS", "WRITE PATTERNS", "NOT SQL HABITS", "MODEL FOR ACCESS"] },
      { slug: "07-embedded-vs-referenced-documents", title: "Embedded vs Referenced Documents", keywords: ["EMBED", "REFERENCE", "ONE DOCUMENT", "SEPARATE COLLECTION"] },
      { slug: "08-index-fundamentals", title: "Index Fundamentals", keywords: ["FASTER READS", "SLOWER WRITES", "B-TREE", "QUERY PLAN"] },
      { slug: "09-aggregation-basics", title: "Aggregation Basics", keywords: ["PIPELINE", "$MATCH", "$GROUP", "TRANSFORM"] },
      { slug: "10-pagination-and-filtering", title: "Pagination and Filtering", keywords: ["CURSOR", "SKIP / LIMIT", "FILTER", "LARGE COLLECTIONS"] },
      { slug: "11-performance-considerations", title: "Performance Considerations", keywords: ["INDEX USAGE", "QUERY SHAPE", "SCHEMA IMPACT", "BOTTLENECKS"] },
      { slug: "12-practical-application-patterns", title: "Practical Application Patterns", keywords: ["REAL PATTERNS", "APPLICATION LAYER", "PUTTING IT TOGETHER", "MONGODB IN PRACTICE"] },
    ],
  },
  {
    slug: "kotlin-coroutines",
    label: "KOTLIN COROUTINES IN PRACTICE",
    color: "0x7C3AED",
    accent: "0xC4B5FD",
    arabic: false,
    lessons: [
      { slug: "01-kotlin-coroutines-fundamentals", title: "Kotlin Coroutines Fundamentals", keywords: ["SUSPEND", "SCOPE", "DISPATCHER", "STRUCTURED CONCURRENCY"] },
      { slug: "02-suspend-functions", title: "Suspend Functions", keywords: ["PAUSE", "RESUME", "NO BLOCKING", "COMPILER TRANSFORM"] },
      { slug: "03-coroutine-builders", title: "Coroutine Builders", keywords: ["launch", "async", "runBlocking", "WHEN TO USE"] },
      { slug: "04-coroutinescope", title: "CoroutineScope", keywords: ["LIFETIME", "AUTO-CANCEL", "SCOPE BOUNDARY", "STRUCTURE"] },
      { slug: "05-jobs-and-cancellation", title: "Jobs and Cancellation", keywords: ["Job", "cancel()", "COOPERATIVE", "NO LEAKS"] },
      { slug: "06-dispatchers-and-context", title: "Dispatchers and Context", keywords: ["Main", "IO", "Default", "THREAD POOL"] },
      { slug: "07-exception-handling", title: "Exception Handling", keywords: ["try / catch", "launch vs async", "SupervisorJob", "PROPAGATION"] },
      { slug: "08-structured-concurrency", title: "Structured Concurrency", keywords: ["PARENT SCOPE", "CHILD COROUTINES", "COMPLETE TOGETHER", "SAFETY"] },
      { slug: "09-async-and-await", title: "async and await", keywords: ["CONCURRENT CALLS", "await()", "COMBINE RESULTS", "PARALLELISM"] },
      { slug: "10-flow-fundamentals", title: "Flow Fundamentals", keywords: ["COLD STREAM", "emit()", "collect()", "ASYNC VALUES"] },
      { slug: "11-combining-asynchronous-work", title: "Combining Asynchronous Work", keywords: ["zip", "combine", "flatMapLatest", "PIPELINES"] },
      { slug: "12-real-application-patterns", title: "Real Application Patterns", keywords: ["NETWORK CALLS", "REPOSITORIES", "UI STATE", "PRODUCTION CODE"] },
    ],
  },
  {
    slug: "ux-design",
    label: "أساسيات تصميم تجربة المستخدم",
    color: "0xBE185D",
    accent: "0xF9A8D4",
    arabic: true,
    lessons: [
      { slug: "01-ux-design-fundamentals", title: "أساسيات تجربة المستخدم", keywords: ["أبحاث المستخدمين", "نماذج أولية", "قابلية الاستخدام", "مسارات المستخدم"] },
      { slug: "02-introduction-to-ux", title: "مقدمة في تجربة المستخدم", keywords: ["ما هي التجربة", "لماذا تهم", "المنتج الرقمي", "نجاح المستخدم"] },
      { slug: "03-user-research-basics", title: "أبحاث المستخدمين الأساسية", keywords: ["مقابلات", "ملاحظة", "رؤى حقيقية", "قبل التصميم"] },
      { slug: "04-analyzing-user-needs", title: "تحليل احتياجات المستخدم", keywords: ["من البحث للاحتياج", "ترتيب الأولويات", "حلول فعلية", "وضوح"] },
      { slug: "05-user-flows", title: "مسارات المستخدم", keywords: ["نقطة البداية", "الخطوات", "الهدف النهائي", "تدفق المهمة"] },
      { slug: "06-information-architecture", title: "هندسة المعلومات", keywords: ["تنظيم المحتوى", "التنقل", "بنية منطقية", "سهولة الإيجاد"] },
      { slug: "07-wireframing", title: "التصميم السلكي", keywords: ["مخططات بسيطة", "بنية الشاشة", "قبل المرئي", "Wireframe"] },
      { slug: "08-interaction-design", title: "تصميم التفاعل", keywords: ["الأزرار", "الانتقالات", "استجابة الواجهة", "التفاعل"] },
      { slug: "09-prototyping", title: "النماذج الأولية", keywords: ["من ثابت لتفاعلي", "اختبار حقيقي", "Prototype", "محاكاة التجربة"] },
      { slug: "10-usability-testing", title: "اختبار قابلية الاستخدام", keywords: ["مستخدمون حقيقيون", "نقاط الالتباس", "قبل الإطلاق", "ملاحظات مباشرة"] },
      { slug: "11-iterating-on-feedback", title: "التكرار بناءً على الملاحظات", keywords: ["تحسين مستمر", "دورات سريعة", "من الملاحظات للتصميم", "تكرار"] },
      { slug: "12-ux-design-handoff", title: "تسليم تصميم تجربة المستخدم", keywords: ["الملفات والمواصفات", "تسليم للمطورين", "دقة التنفيذ", "Handoff"] },
    ],
  },
];

function buildAndRun(course, lesson, index) {
  const arabic = course.arabic;
  const fontRegular = arabic ? "fonts/tahoma.ttf" : "fonts/arial.ttf";
  const fontBold = arabic ? "fonts/tahomabd.ttf" : "fonts/arialbd.ttf";
  const demoLabel = arabic ? "معاينة تجريبية — وسائط DEMO" : "DEMO PREVIEW — not real footage";

  const eyebrowFile = writeTextFile("eyebrow.txt", wrap(course.label, arabic ? 30 : 34));
  const titleFile = writeTextFile("title.txt", wrap(lesson.title, arabic ? 22 : 24));
  const demoFile = writeTextFile("demo.txt", demoLabel);
  const keywordFiles = lesson.keywords.map((kw, i) => writeTextFile(`kw${i}.txt`, kw));

  // Mild, deterministic per-lesson duration variety (19.5s-25.5s) rather than one fixed number.
  const perKeyword = 4 + (index % 3); // 4, 5, or 6 seconds per keyword
  const introHold = 1.5;
  const outroHold = 1.0;
  const duration = introHold + perKeyword * lesson.keywords.length + outroHold;

  const textX = arabic ? "w-text_w-36" : "(w-text_w)/2";
  const demoX = arabic ? "36" : "w-text_w-36";

  const filters = [];
  filters.push(`color=c=${course.color}:s=${WIDTH}x${HEIGHT}:d=${duration}:r=${FPS}[bg]`);

  // A small drifting accent square, same "visible motion, not a static slide" proof D59 used.
  let chain = `[bg]drawbox=x='(w-24)*abs(sin(t/5))':y=26:w=16:h=16:color=${course.accent}@0.6:t=fill[acc]`;
  filters.push(chain);
  let last = "acc";

  const eyebrowY = 34;
  filters.push(
    `[${last}]drawtext=fontfile=${fontBold}:textfile=${eyebrowFile}:fontsize=17:fontcolor=white@0.85:x=${textX}:y=${eyebrowY}:line_spacing=4:text_shaping=1[eb]`,
  );
  last = "eb";

  filters.push(
    `[${last}]drawtext=fontfile=${fontBold}:textfile=${titleFile}:fontsize=30:fontcolor=white:x=${textX}:y=130:line_spacing=8:text_shaping=1[tt]`,
  );
  last = "tt";

  let cursor = introHold;
  lesson.keywords.forEach((_, i) => {
    const start = cursor;
    const end = cursor + perKeyword;
    const label = `kw${i}`;
    filters.push(
      `[${last}]drawtext=fontfile=${fontRegular}:textfile=${keywordFiles[i]}:fontsize=24:fontcolor=${arabic ? "0xFBCFE8" : "0xBFDBFE"}:x=${textX}:y=230:enable='between(t,${start},${end})':text_shaping=1[${label}]`,
    );
    last = label;
    cursor = end;
  });

  filters.push(
    `[${last}]drawtext=fontfile=${fontRegular}:textfile=${demoFile}:fontsize=13:fontcolor=white@0.55:x=${demoX}:y=${HEIGHT - 30}:text_shaping=1[demo]`,
  );
  last = "demo";

  filters.push(`[${last}]fade=t=in:st=0:d=0.5,fade=t=out:st=${(duration - 0.5).toFixed(2)}:d=0.5[out]`);

  const filterComplex = filters.join(";");
  const outDir = slugCourseDir(course.slug);
  const outPath = path.join(outDir, `${lesson.slug}.mp4`);

  execFileSync(
    "ffmpeg",
    [
      "-y",
      "-filter_complex", filterComplex,
      "-map", "[out]",
      "-t", String(duration),
      "-an",
      "-c:v", "libx264",
      "-preset", "veryfast",
      "-crf", "30",
      "-pix_fmt", "yuv420p",
      outPath,
    ],
    { cwd: WORK_DIR, stdio: ["ignore", "ignore", "pipe"] },
  );

  const probe = execFileSync(
    "ffprobe",
    ["-v", "error", "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", outPath],
    { encoding: "utf8" },
  ).trim();
  const realDuration = Math.round(parseFloat(probe));
  const sizeBytes = fs.statSync(outPath).size;

  return {
    course: course.slug,
    slug: lesson.slug,
    resource: `seed-media/lessons/${course.slug}/${lesson.slug}.mp4`,
    durationSeconds: realDuration,
    sizeBytes,
  };
}

function main() {
  const manifest = [];
  let count = 0;
  for (const course of COURSES) {
    course.lessons.forEach((lesson, index) => {
      const entry = buildAndRun(course, lesson, index);
      manifest.push(entry);
      count++;
      console.log(`[${count}/48] ${entry.resource} — ${entry.durationSeconds}s, ${(entry.sizeBytes / 1024).toFixed(0)} KB`);
    });
  }
  const manifestPath = path.join(OUT_ROOT, "generated-lesson-videos.json");
  fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + "\n", "utf8");
  const totalBytes = manifest.reduce((sum, m) => sum + m.sizeBytes, 0);
  console.log(`\nDone. ${manifest.length} videos, ${(totalBytes / 1024 / 1024).toFixed(1)} MB total.`);
  console.log(`Manifest: ${manifestPath}`);
  fs.rmSync(WORK_DIR, { recursive: true, force: true });
}

main();
