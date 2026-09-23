import { Avatar } from "./avatar";

/** design-system/COMPONENTS.md § InstructorCard. No instructor bio/title/photo field exists
 * on the backend yet (only `instructorName`, D37) — avatar falls back to initials, and the
 * "title/expertise" line is omitted rather than fabricated. */
export function InstructorCard({ name }: { name: string }) {
  return (
    <div className="mtx-instructor-card">
      <Avatar name={name} size="large" />
      <div>
        <p className="mtx-text-heading-h4">{name}</p>
      </div>
    </div>
  );
}
