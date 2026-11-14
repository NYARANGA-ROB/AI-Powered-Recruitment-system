import { Skeleton } from 'antd';

interface TableLoadingSkeletonProps {
  columns?: number;
  rows?: number;
}

export default function TableLoadingSkeleton({
  columns = 6,
  rows = 6,
}: TableLoadingSkeletonProps) {
  return (
    <div className="table-loading-skeleton py-2" role="status" aria-label="Loading table data">
      <div className="mb-5 flex gap-4">
        {Array.from({ length: columns }, (_, index) => (
          <div className="min-w-0 flex-1" key={index}>
            <Skeleton.Button active block size="small" />
          </div>
        ))}
      </div>
      <div className="space-y-4">
        {Array.from({ length: rows }, (_, row) => (
          <div className="flex items-center gap-4" key={row}>
            {Array.from({ length: columns }, (_, column) => (
              <div className="min-w-0 flex-1" key={column}>
                <Skeleton.Input active block size="small" style={{ height: 20 }} />
              </div>
            ))}
          </div>
        ))}
      </div>
    </div>
  );
}
