export interface AiTutorQuickActionProps {
  children: React.ReactNode;
  disabled?: boolean;
  onClick: () => void;
}

export function AiTutorQuickAction({ children, disabled = false, onClick }: AiTutorQuickActionProps) {
  return (
    <button type="button" className="mtx-ai-tutor-quick-action" disabled={disabled} onClick={onClick}>
      {children}
    </button>
  );
}
