import { useCallback, useEffect, useState } from 'react';
import {
  Table,
  Card,
  Space,
  Tag,
  Select,
  Typography,
  Button,
  Modal,
  Form,
  InputNumber,
  message,
  Statistic,
  Drawer,
  Progress,
  Descriptions,
  Tooltip,
  Spin,
} from 'antd';
import {
  PlusOutlined,
  ArrowRightOutlined,
  UserOutlined,
  FolderOutlined,
  ThunderboltOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { motion } from 'framer-motion';
import { applicationApi } from '../../api';
import type { ApplicationResponse, ApplicationStatus, ApplicationQueryParams, MatchScoreResponse } from '../../types';
import PageTransition from '../../components/PageTransition';
import { staggerContainer, staggerItem } from '../../components/motionVariants';

const { Text } = Typography;

const statusFlow: Record<ApplicationStatus, ApplicationStatus[]> = {
  PENDING: ['SCREENING', 'REJECTED'],
  SCREENING: ['INTERVIEW', 'REJECTED'],
  INTERVIEW: ['OFFER', 'REJECTED'],
  OFFER: [],
  REJECTED: [],
  WITHDRAWN: [],
};

const statusConfig: Record<ApplicationStatus, { color: string; label: string }> = {
  PENDING: { color: 'default', label: 'Pending' },
  SCREENING: { color: 'processing', label: 'Screening' },
  INTERVIEW: { color: 'warning', label: 'Interviewing' },
  OFFER: { color: 'success', label: 'Hired' },
  REJECTED: { color: 'error', label: 'Rejected' },
  WITHDRAWN: { color: 'default', label: 'Withdrawn' },
};

export default function ApplicationsPage() {
  const [applications, setApplications] = useState<ApplicationResponse[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState<ApplicationQueryParams>({ pageNum: 1, pageSize: 10 });
  const [createModal, setCreateModal] = useState(false);
  const [form] = Form.useForm();
  const [scoreDrawer, setScoreDrawer] = useState(false);
  const [scoreDetail, setScoreDetail] = useState<MatchScoreResponse | null>(null);
  const [scoreLoading, setScoreLoading] = useState(false);
  const [calculatingId, setCalculatingId] = useState<number | null>(null);

  const statusCounts = applications.reduce(
    (acc, app) => {
      acc[app.status] = (acc[app.status] || 0) + 1;
      return acc;
    },
    {} as Record<string, number>
  );

  const loadApplications = useCallback(async () => {
    setLoading(true);
    try {
      const { data } = await applicationApi.list(query);
      setApplications(data.data?.records || []);
      setTotal(data.data?.total || 0);
    } finally {
      setLoading(false);
    }
  }, [query]);

  useEffect(() => {
    loadApplications();
  }, [loadApplications]);

  const handleCreate = async () => {
    const values = await form.validateFields();
    await applicationApi.create(values);
    message.success('Created successfully');
    setCreateModal(false);
    form.resetFields();
    loadApplications();
  };

  const handleStatusChange = async (id: number, newStatus: ApplicationStatus) => {
    await applicationApi.updateStatus(id, { status: newStatus });
    message.success('Status updated');
    loadApplications();
  };

  const handleCalculateScore = async (id: number) => {
    setCalculatingId(id);
    try {
      const { data } = await applicationApi.calculateMatchScore(id);
      if (data.data) {
        setScoreDetail(data.data);
        setScoreDrawer(true);
        message.success('Match score calculated');
        loadApplications();
      }
    } catch {
      message.error('Failed to calculate match score');
    } finally {
      setCalculatingId(null);
    }
  };

  const handleViewScore = async (record: ApplicationResponse) => {
    if (record.matchScore !== undefined && record.matchScore !== null) {
      // Try to fetch fresh score detail
      setScoreDrawer(true);
      setScoreLoading(true);
      try {
        const { data } = await applicationApi.calculateMatchScore(record.id);
        if (data.data) {
          setScoreDetail(data.data);
        }
      } catch {
        // Fallback: show basic score info from list data
        setScoreDetail({
          applicationId: record.id,
          jobId: record.jobId,
          candidateId: record.candidateId,
          totalScore: record.matchScore,
          breakdown: { semanticScore: 0, skillScore: 0, experienceScore: 0, educationScore: 0 },
          matchReasons: record.matchReasons || [],
          calculatedAt: record.matchCalculatedAt || '',
        });
      } finally {
        setScoreLoading(false);
      }
    } else {
      handleCalculateScore(record.id);
    }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    {
      title: 'Job',
      dataIndex: 'jobTitle',
      width: 160,
      render: (v: string) => (
        <Space>
          <FolderOutlined className="text-cyan-600" />
          <Text ellipsis style={{ maxWidth: 120 }}>
            {v || '-'}
          </Text>
        </Space>
      ),
    },
    {
      title: 'Candidate',
      dataIndex: 'candidateName',
      width: 120,
      render: (v: string) => (
        <Space>
          <UserOutlined />
          {v || '-'}
        </Space>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 100,
      render: (status: ApplicationStatus) => (
        <Tag color={statusConfig[status]?.color}>{statusConfig[status]?.label}</Tag>
      ),
    },
    {
      title: 'Match Score',
      dataIndex: 'matchScore',
      width: 140,
      render: (score: number, record: ApplicationResponse) => {
        if (score === undefined || score === null) {
          return (
            <Tooltip title="Calculate AI match score">
              <Button
                type="link"
                size="small"
                icon={<ThunderboltOutlined />}
                loading={calculatingId === record.id}
                onClick={() => handleCalculateScore(record.id)}
              >
                Calculate
              </Button>
            </Tooltip>
          );
        }
        const color =
          score >= 80
            ? '#10b981'
            : score >= 60
              ? '#f59e0b'
              : '#ef4444';
        return (
          <Tooltip title="Click to view match details">
            <span
              className="cursor-pointer hover:opacity-70 transition-opacity"
              onClick={() => handleViewScore(record)}
            >
              <Progress
                type="circle"
                percent={score}
                size={36}
                strokeColor={color}
                format={(p) => <span className="text-xs font-bold">{p}</span>}
              />
            </span>
          </Tooltip>
        );
      },
    },
    { title: 'Applied At', dataIndex: 'createdAt', width: 160 },
    {
      title: 'Actions',
      width: 160,
      render: (_: unknown, record: ApplicationResponse) => {
        const nextStatuses = statusFlow[record.status] || [];
        if (nextStatuses.length === 0)
          return <Text type="secondary">-</Text>;
        return (
          <Space size={4} wrap>
            {nextStatuses.map((s) => (
              <Button
                key={s}
                type="link"
                size="small"
                danger={s === 'REJECTED'}
                onClick={() => handleStatusChange(record.id, s)}
              >
                {statusConfig[s]?.label} <ArrowRightOutlined />
              </Button>
            ))}
          </Space>
        );
      },
    },
  ];

  return (
    <PageTransition>
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-xl font-semibold text-gray-900 m-0">Job Applications</h2>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => setCreateModal(true)}
        >
          New Application
        </Button>
      </div>

      <motion.div variants={staggerContainer} initial="initial" animate="animate">
        {/* Status summary cards */}
        <motion.div variants={staggerItem}>
          <div className="grid grid-cols-3 sm:grid-cols-6 gap-3 sm:gap-4 mb-4">
            {Object.entries(statusConfig).map(([key, cfg]) => (
              <Card bordered={false} key={key} className="text-center">
                <Statistic
                  title={<Tag color={cfg.color}>{cfg.label}</Tag>}
                  value={statusCounts[key] || 0}
                  valueStyle={{ fontSize: 20, fontWeight: 700 }}
                />
              </Card>
            ))}
          </div>
        </motion.div>

        {/* Filters */}
        <motion.div variants={staggerItem}>
          <Card bordered={false} className="mb-4">
            <div className="grid grid-cols-1 md:grid-cols-12 gap-3">
              <div className="md:col-span-3">
                <Select
                  placeholder="Status"
                  allowClear
                  className="w-full"
                  onChange={(v) => setQuery((q) => ({ ...q, status: v, pageNum: 1 }))}
                  options={Object.entries(statusConfig).map(([k, v]) => ({
                    value: k,
                    label: v.label,
                  }))}
                />
              </div>
              <div className="md:col-span-3">
                <InputNumber
                  placeholder="Job ID"
                  min={1}
                  className="w-full"
                  onChange={(v) =>
                    setQuery((q) => ({ ...q, jobId: v ?? undefined, pageNum: 1 }))
                  }
                />
              </div>
              <div className="md:col-span-3">
                <InputNumber
                  placeholder="Candidate ID"
                  min={1}
                  className="w-full"
                  onChange={(v) =>
                    setQuery((q) => ({ ...q, candidateId: v ?? undefined, pageNum: 1 }))
                  }
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
              dataSource={applications}
              loading={loading}
              scroll={{ x: true }}
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

      {/* New application modal */}
      <Modal
        title="New Job Application"
        open={createModal}
        onCancel={() => setCreateModal(false)}
        onOk={handleCreate}
        okText="Create"
      >
        <Form form={form} layout="vertical" className="mt-4">
          <Form.Item
            name="jobId"
            label="Job ID"
            rules={[{ required: true, message: 'Please enter the job ID' }]}
          >
            <InputNumber min={1} className="w-full" placeholder="Enter job ID" />
          </Form.Item>
          <Form.Item
            name="candidateId"
            label="Candidate ID"
            rules={[{ required: true, message: 'Please enter the candidate ID' }]}
          >
            <InputNumber min={1} className="w-full" placeholder="Enter candidate ID" />
          </Form.Item>
        </Form>
      </Modal>

      {/* Match score detail drawer */}
      <Drawer
        title="AI Match Score Details"
        open={scoreDrawer}
        onClose={() => { setScoreDrawer(false); setScoreDetail(null); }}
        width={520}
        extra={
          scoreDetail && (
            <Button
              icon={<ReloadOutlined />}
              loading={calculatingId === scoreDetail.applicationId}
              onClick={() => handleCalculateScore(scoreDetail.applicationId)}
            >
              Recalculate
            </Button>
          )
        }
      >
        <Spin spinning={scoreLoading}>
          {scoreDetail && (
            <div className="space-y-6">
              {/* Overall score */}
              <div className="text-center py-4">
                <Progress
                  type="dashboard"
                  percent={scoreDetail.totalScore}
                  size={160}
                  strokeColor={{
                    '0%': scoreDetail.totalScore >= 70 ? '#10b981' : scoreDetail.totalScore >= 50 ? '#f59e0b' : '#ef4444',
                    '100%': scoreDetail.totalScore >= 70 ? '#059669' : scoreDetail.totalScore >= 50 ? '#d97706' : '#dc2626',
                  }}
                  format={(p) => (
                    <div>
                      <div className="text-3xl font-bold">{p}</div>
                      <div className="text-xs text-gray-500">Overall Match Score</div>
                    </div>
                  )}
                />
              </div>

              {/* Score breakdown */}
              <Card title="Multi-Dimensional Scores" bordered={false} className="!shadow-none !bg-gray-50">
                <div className="space-y-4">
                  {[
                    { label: 'Skill Match', value: scoreDetail.breakdown.skillScore, weight: '35%', color: '#3b82f6' },
                    { label: 'Semantic Similarity', value: scoreDetail.breakdown.semanticScore, weight: '30%', color: '#8b5cf6' },
                    { label: 'Experience Match', value: scoreDetail.breakdown.experienceScore, weight: '20%', color: '#f59e0b' },
                    { label: 'Education Match', value: scoreDetail.breakdown.educationScore, weight: '15%', color: '#10b981' },
                  ].map((dim) => (
                    <div key={dim.label}>
                      <div className="flex justify-between mb-1">
                        <span className="text-sm text-gray-700">
                          {dim.label}
                          <span className="text-xs text-gray-400 ml-1">({dim.weight})</span>
                        </span>
                        <span className="text-sm font-semibold">{Number(dim.value).toFixed(1)}</span>
                      </div>
                      <Progress
                        percent={Number(dim.value)}
                        showInfo={false}
                        strokeColor={dim.color}
                        size="small"
                      />
                    </div>
                  ))}
                </div>
              </Card>

              {/* Match reasons */}
              {scoreDetail.matchReasons?.length > 0 && (
                <Card title="Match Reasons" bordered={false} className="!shadow-none !bg-gray-50">
                  <ul className="list-disc pl-4 space-y-1 m-0">
                    {scoreDetail.matchReasons.map((reason, i) => (
                      <li key={i} className="text-sm text-gray-700">{reason}</li>
                    ))}
                  </ul>
                </Card>
              )}

              {/* Basic information */}
              <Descriptions column={2} size="small" bordered>
                <Descriptions.Item label="Application ID">{scoreDetail.applicationId}</Descriptions.Item>
                <Descriptions.Item label="Job ID">{scoreDetail.jobId}</Descriptions.Item>
                <Descriptions.Item label="Candidate ID">{scoreDetail.candidateId}</Descriptions.Item>
                <Descriptions.Item label="Calculated At">{scoreDetail.calculatedAt || '-'}</Descriptions.Item>
              </Descriptions>
            </div>
          )}
        </Spin>
      </Drawer>
    </PageTransition>
  );
}
