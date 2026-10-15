export default function Skeleton({ className = "", ...props }) {
  return (
    <div
      className={`animate-pulse bg-gray-200 rounded-ui ${className}`}
      {...props}
    />
  );
}

export function CardSkeleton() {
  return (
    <div className="bg-white border border-border rounded-ui p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="flex-1">
          <Skeleton className="h-5 w-3/4" />
          <Skeleton className="h-3.5 w-1/4 mt-2" />
        </div>
        <Skeleton className="h-5 w-12" />
      </div>
      <div className="flex gap-1.5 mt-3">
        <Skeleton className="h-5 w-16 rounded-full" />
        <Skeleton className="h-5 w-12 rounded-full" />
      </div>
    </div>
  );
}

export function ListSkeleton({ rows = 5 }) {
  return (
    <div className="stack-y-2">
      {Array.from({ length: rows }, (_, i) => (
        <Skeleton key={i} className="h-12 w-full" />
      ))}
    </div>
  );
}
