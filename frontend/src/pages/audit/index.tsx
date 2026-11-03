import { useCallback, useEffect, useState } from 'react';
import {
  Table,
  Card,
  Space,
  Tag,
  Select,
  Input,
  DatePicker,
  Typography,
  Drawer,
  Descriptions,
  Badge,
  InputNumber,
} from 'antd';
import {
  ClockCircleOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SearchOutlined,
  EyeOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { motion } from 'framer-motion';
import dayjs from 'dayjs';
import { auditApi } from '../../api';
import type { AuditLogResponse, AuditLogQueryRequest } from '../../types';
import PageTransition from '../../components/PageTransition';
import { staggerContainer, staggerItem } from '../../components/motionVariants';

const { Text } = Typography;
const { RangePicker } = DatePicker;

const moduleOptions = [
  { value: 'Job Management', label: 'Job Management' },
  { value: 'Resume Management', label: 'Resume Management' },
  { value: 'Candidate Management', label: 'Candidate Management' },
  { value: 'Job Applications', label: 'Job Applications' },
  { value: 'Interview Management', label: 'Interview Management' },
  { value: 'WEBHOOK', label: 'Webhook' },
  { value: 'ANALYTICS', label: 'Recruiting Analytics' },
];

const statusConfig = {
  SUCCESS: { color: 'success', icon: <CheckCircleOutlined />, label: 'Success' },
  FAILED: { color: 'error', icon: <CloseCircleOutlined />, label: 'Failed' },
} as const;

export default function AuditLogPage() {
  const [logs, setLogs] = useState<AuditLogResponse[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState<AuditLogQueryRequest>({ pageNum: 1, pageSize: 20 });
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [selectedLog, setSelectedLog] = useState<AuditLogResponse | null>(null);

  const loadLogs = useCallback(async () => {
    setLoading(true);
    try {
      const { data } = await auditApi.list(query);
      setLogs(data.data?.records || []);
      setTotal(data.data?.total || 0);
    } finally {
      setLoading(false);
    }
  }, [query]);

  useEffect(() => {
    loadLogs();
  }, [loadLogs]);

  const handleViewDetail = (record: AuditLogResponse) => {
    setSelectedLog(record);
    setDrawerOpen(true);
  };

  const columns: ColumnsType<AuditLogResponse> = [
    {
      title: 'Time',
      dataIndex: 'createdAt',
      width: 170,
      render: (v: string) => (
        <Space size={4}>
          <ClockCircleOutlined className="text-gray-400" />
          <Text className="text-xs">{v}</Text>
        </Space>
      ),
    },
    {
      title: 'User',
      dataIndex: 'username',
      width: 100,
      ellipsis: true,
    },
    {
      title: 'Module',
      dataIndex: 'module',
      width: 100,
      render: (v: string) => <Tag>{v}</Tag>,
    },
    {
      title: 'Action',
      dataIndex: 'operation',
      width: 120,
      render: (v: string) => <Tag color="blue">{v}</Tag>,
    },
    {
      title: 'Description',
      dataIndex: 'description',
      ellipsis: true,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 80,
      render: (status: 'SUCCESS' | 'FAILED') => {
        const cfg = statusConfig[status];
        return (
          <Tag color={cfg.color} icon={cfg.icon}>
            {cfg.label}
          </Tag>
        );
      },
    },
    {
      title: 'Duration',
      dataIndex: 'duration',
      width: 80,
      render: (v: number) => {
        const color = v > 1000 ? 'text-red-500' : v > 300 ? 'text-amber-500' : 'text-green-600';
        return <span className={`font-mono text-xs ${color}`}>{v}ms</span>;
      },
    },
    {
      title: 'IP',
      dataIndex: 'requestIp',
      width: 120,
      render: (v: string) => <Text className="font-mono text-xs">{v}</Text>,
    },
    {
      title: 'Actions',
      width: 60,
      render: (_: unknown, record: AuditLogResponse) => (
        <EyeOutlined
          className="text-gray-500 hover:text-blue-500 cursor-pointer transition-colors"
          onClick={() => handleViewDetail(record)}
        />
      ),
    },
  ];

  const handleDateChange = (_: unknown, dateStrings: [string, string]) => {
    setQuery((q) => ({
      ...q,
      startTime: dateStrings[0] ? `${dateStrings[0]} 00:00:00` : undefined,
      endTime: dateStrings[1] ? `${dateStrings[1]} 23:59:59` : undefined,
      pageNum: 1,
    }));
  };

  // Statistics
  const successCount = logs.filter((l) => l.status === 'SUCCESS').length;
  const failedCount = logs.filter((l) => l.status === 'FAILED').length;
  const avgDuration = logs.length
    ? Math.round(logs.reduce((sum, l) => sum + l.duration, 0) / logs.length)
    : 0;

  return (
    <PageTransition>
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-xl font-semibold text-gray-900 m-0">Audit Logs</h2>
        <Text type="secondary" className="text-sm">
          {total} records in total
        </Text>
      </div>

      <motion.div variants={staggerContainer} initial="initial" animate="animate">
        {/* Statistics cards */}
        <motion.div variants={staggerItem}>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 sm:gap-4 mb-4">
            <Card bordered={false}>
              <div className="text-center">
                <div className="text-2xl font-bold text-gray-900">{total}</div>
                <div className="text-xs text-gray-500 mt-1">Total Records</div>
              </div>
            </Card>
            <Card bordered={false}>
              <div className="text-center">
                <div className="text-2xl font-bold text-green-600">{successCount}</div>
                <div className="text-xs text-gray-500 mt-1">Success (this page)</div>
              </div>
            </Card>
            <Card bordered={false}>
              <div className="text-center">
                <div className="text-2xl font-bold text-red-500">{failedCount}</div>
                <div className="text-xs text-gray-500 mt-1">Failed (this page)</div>
              </div>
            </Card>
            <Card bordered={false}>
              <div className="text-center">
                <div className="text-2xl font-bold text-blue-600">{avgDuration}ms</div>
                <div className="text-xs text-gray-500 mt-1">Avg. Duration (this page)</div>
              </div>
            </Card>
          </div>
        </motion.div>

        {/* Filters */}
        <motion.div variants={staggerItem}>
          <Card bordered={false} className="mb-4">
            <div className="grid grid-cols-1 md:grid-cols-12 gap-3">
              <div className="md:col-span-2">
                <Select
                  placeholder="Module"
                  allowClear
                  className="w-full"
                  options={moduleOptions}
                  onChange={(v) => setQuery((q) => ({ ...q, module: v, pageNum: 1 }))}
                />
              </div>
              <div className="md:col-span-2">
                <Select
                  placeholder="Status"
                  allowClear
                  className="w-full"
                  options={[
                    { value: 'SUCCESS', label: 'Success' },
                    { value: 'FAILED', label: 'Failed' },
                  ]}
                  onChange={(v) => setQuery((q) => ({ ...q, status: v, pageNum: 1 }))}
                />
              </div>
              <div className="md:col-span-2">
                <InputNumber
                  placeholder="User ID"
                  min={1}
                  className="w-full"
                  onChange={(v) =>
                    setQuery((q) => ({ ...q, userId: v ?? undefined, pageNum: 1 }))
                  }
                />
              </div>
              <div className="md:col-span-3">
                <RangePicker
                  className="w-full"
                  onChange={handleDateChange}
                  placeholder={['Start date', 'End date']}
                />
              </div>
              <div className="md:col-span-3">
                <Input
                  placeholder="Search description keywords..."
                  prefix={<SearchOutlined />}
                  allowClear
                  onPressEnter={(e) =>
                    setQuery((q) => ({
                      ...q,
                      keyword: (e.target as HTMLInputElement).value || undefined,
                      pageNum: 1,
                    }))
                  }
                  onChange={(e) => {
                    if (!e.target.value) {
                      setQuery((q) => ({ ...q, keyword: undefined, pageNum: 1 }));
                    }
                  }}
                />
              </div>
            </div>
          </Card>
        </motion.div>

        {/* Table */}
        <motion.div variants={staggerItem}>
          <Card bordered={false}>
            <Table
              rowKey="id"
              columns={columns}
              dataSource={logs}
              loading={loading}
              scroll={{ x: 1000 }}
              size="small"
              pagination={{
                current: query.pageNum,
                pageSize: query.pageSize,
                total,
                showSizeChanger: true,
                showTotal: (t) => `${t} records in total`,
                onChange: (p, s) =>
                  setQuery((q) => ({ ...q, pageNum: p, pageSize: s })),
              }}
            />
          </Card>
        </motion.div>
      </motion.div>

      {/* Detail drawer */}
      <Drawer
        title="Audit Log Details"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={560}
      >
        {selectedLog && (
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="ID">{selectedLog.id}</Descriptions.Item>
            <Descriptions.Item label="Time">{selectedLog.createdAt}</Descriptions.Item>
            <Descriptions.Item label="User">
              {selectedLog.username} (ID: {selectedLog.userId})
            </Descriptions.Item>
            <Descriptions.Item label="Module">
              <Tag>{selectedLog.module}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Action">
              <Tag color="blue">{selectedLog.operation}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Description">{selectedLog.description}</Descriptions.Item>
            <Descriptions.Item label="Status">
              <Badge
                status={selectedLog.status === 'SUCCESS' ? 'success' : 'error'}
                text={selectedLog.status === 'SUCCESS' ? 'Success' : 'Failed'}
              />
            </Descriptions.Item>
            {selectedLog.errorMessage && (
              <Descriptions.Item label="Error Message">
                <Text type="danger">{selectedLog.errorMessage}</Text>
              </Descriptions.Item>
            )}
            <Descriptions.Item label="Execution Duration">
              <span
                className={`font-mono ${selectedLog.duration > 1000 ? 'text-red-500' : selectedLog.duration > 300 ? 'text-amber-500' : 'text-green-600'}`}
              >
                {selectedLog.duration}ms
              </span>
            </Descriptions.Item>
            <Descriptions.Item label="HTTP Method">
              <Tag color="purple">{selectedLog.requestMethod}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Request Path">
              <Text className="font-mono text-xs" copyable>
                {selectedLog.requestUrl}
              </Text>
            </Descriptions.Item>
            <Descriptions.Item label="Java Method">
              <Text className="font-mono text-xs" ellipsis>
                {selectedLog.method}
              </Text>
            </Descriptions.Item>
            <Descriptions.Item label="Request IP">
              <Text className="font-mono">{selectedLog.requestIp}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="Request Parameters">
              <div className="max-h-40 overflow-auto">
                <pre className="text-xs bg-gray-50 p-2 rounded m-0 whitespace-pre-wrap">
                  {(() => {
                    try {
                      return JSON.stringify(
                        JSON.parse(selectedLog.requestParams || '{}'),
                        null,
                        2
                      );
                    } catch {
                      return selectedLog.requestParams || '-';
                    }
                  })()}
                </pre>
              </div>
            </Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>
    </PageTransition>
  );
}
