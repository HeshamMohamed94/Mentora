function initials(name: string): string {
  const parts = name.trim().split(/\s+/);
  return parts
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? "")
    .join("");
}

/** design-system/COMPONENTS.md § InstructorCard. No instructor bio/title/photo field exists
 * on the backend yet (only `instructorName`, D37) — avatar falls back to initials, and the
 * "title/expertise" line is omitted rather than fabricated. */
export function InstructorCard({ name }: { name: string }) {
  return (
    <div className="mtx-instructor-card">
      <div className="mtx-avatar" style={{ width: 64, height: 64, fontSize: 24 }} aria-hidden="true">
        {initials(name)}
      </div>
      <div>
        <p className="mtx-text-heading-h4">{name}</p>
      </div>
    </div>
  );
}
