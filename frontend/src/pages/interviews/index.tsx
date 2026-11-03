import { useEffect, useState } from 'react';
import {
  Table,
  Card,
  Space,
  Tag,
  Typography,
  Button,
  Modal,
  Form,
  Input,
  InputNumber,
  Select,
  DatePicker,
  message,
  Popconfirm,
  Descriptions,
  Drawer,
  Divider,
} from 'antd';
import {
  PlusOutlined,
  CalendarOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ClockCircleOutlined,
  EditOutlined,
  StopOutlined,
  EyeOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons';
import { motion } from 'framer-motion';
import { interviewApi } from '../../api';
import type { InterviewResponse, InterviewStatus, InterviewType, Recommendation } from '../../types';
import PageTransition from '../../components/PageTransition';
import { staggerContainer, staggerItem } from '../../components/motionVariants';

const { Paragraph } = Typography;

const statusConfig: Record<
  InterviewStatus,
  { color: string; label: string; icon: React.ReactNode }
> = {
  SCHEDULED: { color: 'processing', label: 'Scheduled', icon: <ClockCircleOutlined /> },
  COMPLETED: { color: 'success', label: 'Completed', icon: <CheckCircleOutlined /> },
  CANCELLED: { color: 'error', label: 'Cancelled', icon: <CloseCircleOutlined /> },
  NO_SHOW: { color: 'warning', label: 'No-show', icon: <ExclamationCircleOutlined /> },
};

const typeConfig: Record<InterviewType, string> = {
  PHONE: 'Phone Screen',
  VIDEO: 'Video Interview',
  ONSITE: 'Onsite Interview',
  WRITTEN_TEST: 'Written Test',
};

const recommendConfig: Record<Recommendation, { color: string; label: string }> = {
  STRONG_YES: { color: '#10b981', label: 'Strong Hire' },
  YES: { color: '#22c55e', label: 'Hire' },
  NEUTRAL: { color: '#f59e0b', label: 'Neutral' },
  NO: { color: '#ef4444', label: 'No Hire' },
  STRONG_NO: { color: '#dc2626', label: 'Strong No Hire' },
};

export default function InterviewsPage() {
  const [interviews, setInterviews] = useState<InterviewResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [scheduleModal, setScheduleModal] = useState(false);
  const [feedbackModal, setFeedbackModal] = useState<InterviewResponse | null>(null);
  const [detailDrawer, setDetailDrawer] = useState<InterviewResponse | null>(null);
  const [applicationIdFilter, setApplicationIdFilter] = useState<number | undefined>();
  const [form] = Form.useForm();
  const [feedbackForm] = Form.useForm();

  useEffect(() => {
    if (applicationIdFilter) loadByApplication(applicationIdFilter);
  }, [applicationIdFilter]);

  const loadByApplication = async (appId: number) => {
    setLoading(true);
    try {
      const { data } = await interviewApi.listByApplication(appId);
      setInterviews(data.data || []);
    } finally {
      setLoading(false);
    }
  };

  const handleSchedule = async () => {
    const values = await form.validateFields();
    const payload = {
      ...values,
      scheduledAt: values.scheduledAt.format('YYYY-MM-DD HH:mm:ss'),
    };
    await interviewApi.schedule(payload);
    message.success('Interview scheduled');
    setScheduleModal(false);
    form.resetFields();
    if (applicationIdFilter) loadByApplication(applicationIdFilter);
  };

  const handleFeedback = async () => {
    const values = await feedbackForm.validateFields();
    await interviewApi.submitFeedback(feedbackModal!.id, values);
    message.success('Feedback submitted');
    setFeedbackModal(null);
    feedbackForm.resetFields();
    if (applicationIdFilter) loadByApplication(applicationIdFilter);
  };

  const handleCancel = async (id: number) => {
    await interviewApi.cancel(id);
    message.success('Interview cancelled');
    if (applicationIdFilter) loadByApplication(applicationIdFilter);
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: 'Application ID', dataIndex: 'applicationId', width: 80 },
    {
      title: 'Round',
      dataIndex: 'round',
      width: 70,
      render: (v: number) => `Round ${v}`,
    },
    {
      title: 'Type',
      dataIndex: 'interviewType',
      width: 100,
      render: (type: InterviewType) => typeConfig[type] || type,
    },
    { title: 'Interviewer', dataIndex: 'interviewerName', width: 100 },
    {
      title: 'Scheduled At',
      dataIndex: 'scheduledAt',
      width: 160,
      render: (v: string) => (
        <span className="text-gray-600">
          <CalendarOutlined className="text-cyan-600 mr-1" />
          {v}
        </span>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 100,
      render: (status: InterviewStatus) => {
        const cfg = statusConfig[status];
        return cfg ? (
          <Tag icon={cfg.icon} color={cfg.color}>
            {cfg.label}
          </Tag>
        ) : (
          status
        );
      },
    },
    {
      title: 'Score',
      dataIndex: 'score',
      width: 80,
      render: (score: number) =>
        score !== undefined && score !== null ? (
          <span className="font-semibold">{score} pts</span>
        ) : (
          '-'
        ),
    },
    {
      title: 'Recommendation',
      dataIndex: 'recommendation',
      width: 120,
      render: (rec: Recommendation) => {
        if (!rec) return '-';
        const cfg = recommendConfig[rec];
        return cfg ? (
          <span style={{ color: cfg.color }} className="font-semibold">
            {cfg.label}
          </span>
        ) : (
          rec
        );
      },
    },
    {
      title: 'Actions',
      width: 160,
      render: (_: unknown, record: InterviewResponse) => (
        <Space size={4} wrap>
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => setDetailDrawer(record)}
          >
            Details
          </Button>
          {record.status === 'SCHEDULED' && (
            <>
              <Button
                type="link"
                size="small"
                icon={<EditOutlined />}
                onClick={() => {
                  setFeedbackModal(record);
                  feedbackForm.resetFields();
                }}
              >
                Feedback
              </Button>
              <Popconfirm
                title="Are you sure you want to cancel this interview?"
                onConfirm={() => handleCancel(record.id)}
              >
                <Button type="link" size="small" icon={<StopOutlined />} danger>
                  Cancel
                </Button>
              </Popconfirm>
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <PageTransition>
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-xl font-semibold text-gray-900 m-0">Interview Management</h2>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => setScheduleModal(true)}
        >
          Schedule Interview
        </Button>
      </div>

      <motion.div variants={staggerContainer} initial="initial" animate="animate">
        {/* Filters */}
        <motion.div variants={staggerItem}>
          <Card bordered={false} className="mb-4">
            <div className="flex flex-col sm:flex-row gap-4 items-start sm:items-center">
              <InputNumber
                placeholder="Enter application ID to look up interviews"
                min={1}
                className="w-full sm:w-64"
                value={applicationIdFilter}
                onChange={(v) => setApplicationIdFilter(v ?? undefined)}
              />
              <Button
                type="primary"
                onClick={() =>
                  applicationIdFilter && loadByApplication(applicationIdFilter)
                }
              >
                Search
              </Button>
            </div>
          </Card>
        </motion.div>

        {/* Table */}
        <motion.div variants={staggerItem}>
          <Card bordered={false}>
            {applicationIdFilter ? (
              <Table
                rowKey="id"
                columns={columns}
                dataSource={interviews}
                loading={loading}
                scroll={{ x: true }}
                pagination={false}
              />
            ) : (
              <div className="text-center py-12 text-gray-400">
                <CalendarOutlined className="text-5xl mb-4 block" />
                <p>Enter an application ID to look up interview records</p>
              </div>
            )}
          </Card>
        </motion.div>
      </motion.div>

      {/* Schedule interview modal */}
      <Modal
        title="Schedule Interview"
        open={scheduleModal}
        onCancel={() => setScheduleModal(false)}
        onOk={handleSchedule}
        okText="Confirm"
        width={520}
      >
        <Form form={form} layout="vertical" className="mt-4">
          <Form.Item name="applicationId" label="Application ID" rules={[{ required: true }]}>
            <InputNumber min={1} className="w-full" placeholder="Job application ID" />
          </Form.Item>
          <div className="grid grid-cols-2 gap-4">
            <Form.Item name="round" label="Interview Round" rules={[{ required: true }]}>
              <InputNumber min={1} max={10} className="w-full" />
            </Form.Item>
            <Form.Item name="interviewType" label="Interview Type" rules={[{ required: true }]}>
              <Select
                options={[
                  { value: 'PHONE', label: 'Phone Screen' },
                  { value: 'VIDEO', label: 'Video Interview' },
                  { value: 'ONSITE', label: 'Onsite Interview' },
                  { value: 'WRITTEN_TEST', label: 'Written Test' },
                ]}
              />
            </Form.Item>
          </div>
          <Form.Item name="scheduledAt" label="Interview Time" rules={[{ required: true }]}>
            <DatePicker showTime className="w-full" />
          </Form.Item>
          <div className="grid grid-cols-2 gap-4">
            <Form.Item name="interviewerId" label="Interviewer ID" rules={[{ required: true }]}>
              <InputNumber min={1} className="w-full" />
            </Form.Item>
            <Form.Item name="durationMinutes" label="Duration (minutes)">
              <InputNumber min={15} max={480} className="w-full" placeholder="e.g. 60" />
            </Form.Item>
          </div>
        </Form>
      </Modal>

      {/* Feedback modal */}
      <Modal
        title={`Submit Interview Feedback - Round ${feedbackModal?.round || ''}`}
        open={!!feedbackModal}
        onCancel={() => setFeedbackModal(null)}
        onOk={handleFeedback}
        okText="Submit Feedback"
        width={520}
      >
        <Form form={feedbackForm} layout="vertical" className="mt-4">
          <Form.Item name="score" label="Interview Score (1-100)" rules={[{ required: true }]}>
            <InputNumber min={1} max={100} className="w-full" />
          </Form.Item>
          <Form.Item name="recommendation" label="Recommendation" rules={[{ required: true }]}>
            <Select
              options={Object.entries(recommendConfig).map(([k, v]) => ({
                value: k,
                label: v.label,
              }))}
            />
          </Form.Item>
          <Form.Item name="feedback" label="Detailed Feedback" rules={[{ required: true }]}>
            <Input.TextArea rows={4} placeholder="Describe your interview assessment..." />
          </Form.Item>
        </Form>
      </Modal>

      {/* Detail drawer */}
      <Drawer
        title={`Interview Details #${detailDrawer?.id || ''}`}
        open={!!detailDrawer}
        onClose={() => setDetailDrawer(null)}
        width={480}
      >
        {detailDrawer && (
          <div className="space-y-6">
            <Descriptions column={2}>
              <Descriptions.Item label="Application ID">
                {detailDrawer.applicationId}
              </Descriptions.Item>
              <Descriptions.Item label="Round">
                Round {detailDrawer.round}
              </Descriptions.Item>
              <Descriptions.Item label="Type">
                {typeConfig[detailDrawer.interviewType]}
              </Descriptions.Item>
              <Descriptions.Item label="Interviewer">
                {detailDrawer.interviewerName || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="Time">
                {detailDrawer.scheduledAt}
              </Descriptions.Item>
              <Descriptions.Item label="Duration">
                {detailDrawer.durationMinutes
                  ? `${detailDrawer.durationMinutes} min`
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="Status" span={2}>
                <Tag color={statusConfig[detailDrawer.status]?.color}>
                  {statusConfig[detailDrawer.status]?.label}
                </Tag>
              </Descriptions.Item>
            </Descriptions>

            {detailDrawer.score !== undefined &&
              detailDrawer.score !== null && (
              <>
                <Divider className="my-3" />
                <h4 className="font-semibold text-gray-900 mb-3">Interview Feedback</h4>
                <Descriptions column={2}>
                  <Descriptions.Item label="Score">
                    <span className="text-lg font-semibold">{detailDrawer.score}</span>{' '}
                    / 100
                  </Descriptions.Item>
                  <Descriptions.Item label="Recommendation">
                    {detailDrawer.recommendation && (
                      <span
                        style={{
                          color:
                            recommendConfig[detailDrawer.recommendation]?.color,
                        }}
                        className="font-semibold"
                      >
                        {recommendConfig[detailDrawer.recommendation]?.label}
                      </span>
                    )}
                  </Descriptions.Item>
                </Descriptions>
                {detailDrawer.feedback && (
                  <div>
                    <p className="font-semibold text-gray-900 mb-2">Detailed Feedback</p>
                    <Paragraph className="whitespace-pre-wrap text-gray-600">
                      {detailDrawer.feedback}
                    </Paragraph>
                  </div>
                )}
              </>
            )}
          </div>
        )}
      </Drawer>
    </PageTransition>
  );
}
