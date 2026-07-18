export function VersionTag({ className = '' }: { className?: string }) {
  return (
    <span className={`text-xs text-gray-500 ${className}`}>
      {/* i18n-ignore: version format prefix, not UI copy */}
      {`v${__APP_VERSION__}`}
    </span>
  )
}
