import { useCallback, useEffect, useState } from 'react';
import {
  Table,
  Button,
  Card,
  Space,
  Tag,
  Input,
  Select,
  Modal,
  Form,
  InputNumber,
  message,
  Popconfirm,
  Typography,
  Drawer,
  Descriptions,
  Divider,
} from 'antd';
import {
  PlusOutlined,
  SearchOutlined,
  EditOutlined,
  DeleteOutlined,
  EyeOutlined,
  SendOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { motion } from 'framer-motion';
import { jobApi } from '../../api';
import type { JobResponse, JobStatus, JobQueryParams } from '../../types';
import PageTransition from '../../components/PageTransition';
import { staggerContainer, staggerItem } from '../../components/motionVariants';

const { Paragraph } = Typography;

const statusConfig: Record<JobStatus, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: 'Draft' },
  PUBLISHED: { color: 'success', label: 'Published' },
  CLOSED: { color: 'error', label: 'Closed' },
};

export default function JobsPage() {
  const [jobs, setJobs] = useState<JobResponse[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState<JobQueryParams>({ pageNum: 1, pageSize: 10 });
  const [modalOpen, setModalOpen] = useState(false);
  const [editingJob, setEditingJob] = useState<JobResponse | null>(null);
  const [detailDrawer, setDetailDrawer] = useState<JobResponse | null>(null);
  const [form] = Form.useForm();

  const loadJobs = useCallback(async () => {
    setLoading(true);
    try {
      const { data } = await jobApi.list(query);
      setJobs(data.data?.records || []);
      setTotal(data.data?.total || 0);
    } finally {
      setLoading(false);
    }
  }, [query]);

  useEffect(() => {
    loadJobs();
  }, [loadJobs]);

  const handleCreate = () => {
    setEditingJob(null);
    form.resetFields();
    setModalOpen(true);
  };

  const handleEdit = (job: JobResponse) => {
    setEditingJob(job);
    form.setFieldsValue({ ...job, requiredSkills: job.requiredSkills?.join(', ') });
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    const skills = values.requiredSkills
      ? values.requiredSkills.split(/[,，]/).map((s: string) => s.trim()).filter(Boolean)
      : undefined;
    const payload = { ...values, requiredSkills: skills };
    if (editingJob) {
      await jobApi.update({ ...payload, id: editingJob.id });
      message.success('Updated successfully');
    } else {
      await jobApi.create(payload);
      message.success('Created successfully');
    }
    setModalOpen(false);
    loadJobs();
  };

  const handlePublish = async (id: number) => {
    await jobApi.publish(id);
    message.success('Published');
    loadJobs();
  };

  const handleClose = async (id: number) => {
    await jobApi.close(id);
    message.success('Closed');
    loadJobs();
  };

  const handleDelete = async (id: number) => {
    await jobApi.delete(id);
    message.success('Deleted');
    loadJobs();
  };

  const columns = [
    {
      title: 'Job Title',
      dataIndex: 'title',
      width: 200,
      render: (text: string, record: JobResponse) => (
        <a
          className="text-gray-900 hover:text-cyan-600 transition-colors font-medium cursor-pointer"
          onClick={() => setDetailDrawer(record)}
        >
          {text}
        </a>
      ),
    },
    {
      title: 'Department',
      dataIndex: 'department',
      width: 120,
      render: (v: string) => <span className="text-gray-500">{v || '-'}</span>,
    },
    {
      title: 'Salary Range',
      dataIndex: 'salaryRange',
      width: 120,
      render: (v: string) => <span className="font-semibold text-amber-600">{v}</span>,
    },
    { title: 'Experience', dataIndex: 'experienceRange', width: 100 },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 90,
      render: (status: JobStatus) => (
        <Tag color={statusConfig[status]?.color}>{statusConfig[status]?.label}</Tag>
      ),
    },
    {
      title: 'Views',
      dataIndex: 'viewCount',
      width: 80,
      sorter: true,
      render: (v: number) => (
        <span className="text-gray-500 flex items-center gap-1">
          <EyeOutlined /> {v}
        </span>
      ),
    },
    { title: 'Created At', dataIndex: 'createdAt', width: 160 },
    {
      title: 'Actions',
      width: 180,
      className: '!overflow-visible',
      render: (_: unknown, record: JobResponse) => (
        <Space size={4} wrap>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            Edit
          </Button>
          {record.status === 'DRAFT' && (
            <Button
              type="link"
              size="small"
              icon={<SendOutlined />}
              onClick={() => handlePublish(record.id)}
            >
              Publish
            </Button>
          )}
          {record.status === 'PUBLISHED' && (
            <Button
              type="link"
              size="small"
              icon={<StopOutlined />}
              danger
              onClick={() => handleClose(record.id)}
            >
              Close
            </Button>
          )}
          <Popconfirm title="Are you sure you want to delete this job?" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" size="small" icon={<DeleteOutlined />} danger>
              Delete
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <PageTransition>
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-xl font-semibold text-gray-900 m-0">Job Management</h2>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
          New Job
        </Button>
      </div>

      {/* Search bar */}
      <motion.div variants={staggerContainer} initial="initial" animate="animate">
        <motion.div variants={staggerItem}>
          <Card bordered={false} className="mb-4">
            <div className="grid grid-cols-1 md:grid-cols-12 gap-4">
              <div className="md:col-span-5">
                <Input
                  placeholder="Search job titles"
                  prefix={<SearchOutlined className="text-gray-400" />}
                  allowClear
                  onChange={(e) => setQuery((q) => ({ ...q, keyword: e.target.value, pageNum: 1 }))}
                />
              </div>
              <div className="md:col-span-3">
                <Select
                  placeholder="Status"
                  allowClear
                  className="w-full"
                  onChange={(v) => setQuery((q) => ({ ...q, status: v, pageNum: 1 }))}
                  options={[
                    { value: 'DRAFT', label: 'Draft' },
                    { value: 'PUBLISHED', label: 'Published' },
                    { value: 'CLOSED', label: 'Closed' },
                  ]}
                />
              </div>
              <div className="md:col-span-3">
                <Select
                  placeholder="Job Type"
                  allowClear
                  className="w-full"
                  onChange={(v) => setQuery((q) => ({ ...q, jobType: v, pageNum: 1 }))}
                  options={[
                    { value: '全职', label: 'Full-time' },
                    { value: '兼职', label: 'Part-time' },
                    { value: '实习', label: 'Internship' },
                  ]}
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
              dataSource={jobs}
              loading={loading}
              scroll={{ x: true }}
              pagination={{
                current: query.pageNum,
                pageSize: query.pageSize,
                total,
                showSizeChanger: true,
                showTotal: (t) => `${t} records in total`,
                onChange: (page, size) => setQuery((q) => ({ ...q, pageNum: page, pageSize: size })),
              }}
            />
          </Card>
        </motion.div>
      </motion.div>

      {/* Create / edit modal */}
      <Modal
        title={editingJob ? 'Edit Job' : 'New Job'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleSubmit}
        width={640}
        okText={editingJob ? 'Update' : 'Create'}
      >
        <Form form={form} layout="vertical" className="mt-4">
          <Form.Item name="title" label="Job Title" rules={[{ required: true }]}>
            <Input placeholder="e.g. Senior Java Engineer" />
          </Form.Item>
          <div className="grid grid-cols-2 gap-4">
            <Form.Item name="department" label="Department">
              <Input placeholder="e.g. Engineering" />
            </Form.Item>
            <Form.Item name="jobType" label="Job Type">
              <Select
                placeholder="Select type"
                options={[
                  { value: '全职', label: 'Full-time' },
                  { value: '兼职', label: 'Part-time' },
                  { value: '实习', label: 'Internship' },
                ]}
              />
            </Form.Item>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Form.Item name="salaryMin" label="Min Salary (K CNY)" rules={[{ required: true }]}>
              <InputNumber min={0} className="w-full" />
            </Form.Item>
            <Form.Item name="salaryMax" label="Max Salary (K CNY)" rules={[{ required: true }]}>
              <InputNumber min={0} className="w-full" />
            </Form.Item>
          </div>
          <div className="grid grid-cols-3 gap-4">
            <Form.Item name="experienceMin" label="Min Experience (years)">
              <InputNumber min={0} className="w-full" />
            </Form.Item>
            <Form.Item name="experienceMax" label="Max Experience (years)">
              <InputNumber min={0} className="w-full" />
            </Form.Item>
            <Form.Item name="education" label="Education Requirement">
              <Select
                placeholder="Select education"
                allowClear
                options={[
                  { value: '大专', label: 'Associate' },
                  { value: '本科', label: "Bachelor's" },
                  { value: '硕士', label: "Master's" },
                  { value: '博士', label: 'PhD' },
                ]}
              />
            </Form.Item>
          </div>
          <Form.Item name="requiredSkills" label="Skill Tags (comma-separated)">
            <Input placeholder="e.g. Java, Spring Boot, MySQL" />
          </Form.Item>
          <Form.Item name="description" label="Job Description" rules={[{ required: true }]}>
            <Input.TextArea rows={3} placeholder="Job description..." />
          </Form.Item>
          <Form.Item name="requirements" label="Requirements" rules={[{ required: true }]}>
            <Input.TextArea rows={3} placeholder="Requirements..." />
          </Form.Item>
        </Form>
      </Modal>

      {/* Detail drawer */}
      <Drawer
        title={detailDrawer?.title}
        open={!!detailDrawer}
        onClose={() => setDetailDrawer(null)}
        width={560}
      >
        {detailDrawer && (
          <div className="space-y-6">
            <Descriptions column={2}>
              <Descriptions.Item label="Department">{detailDrawer.department || '-'}</Descriptions.Item>
              <Descriptions.Item label="Type">{detailDrawer.jobType || 'Full-time'}</Descriptions.Item>
              <Descriptions.Item label="Salary">
                <span className="font-semibold text-amber-600">{detailDrawer.salaryRange}</span>
              </Descriptions.Item>
              <Descriptions.Item label="Experience">{detailDrawer.experienceRange}</Descriptions.Item>
              <Descriptions.Item label="Education">{detailDrawer.education || 'Any'}</Descriptions.Item>
              <Descriptions.Item label="Status">
                <Tag color={statusConfig[detailDrawer.status]?.color}>
                  {statusConfig[detailDrawer.status]?.label}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Views">{detailDrawer.viewCount}</Descriptions.Item>
              <Descriptions.Item label="Created At">{detailDrawer.createdAt}</Descriptions.Item>
            </Descriptions>
            <Divider className="my-4" />
            {detailDrawer.requiredSkills?.length > 0 && (
              <div>
                <p className="font-semibold text-gray-900 mb-2">Skill Tags</p>
                <div className="flex flex-wrap gap-1.5">
                  {detailDrawer.requiredSkills.map((s) => (
                    <Tag color="blue" key={s}>
                      {s}
                    </Tag>
                  ))}
                </div>
              </div>
            )}
            <div>
              <p className="font-semibold text-gray-900 mb-2">Job Description</p>
              <Paragraph className="whitespace-pre-wrap text-gray-600">
                {detailDrawer.description}
              </Paragraph>
            </div>
            <div>
              <p className="font-semibold text-gray-900 mb-2">Requirements</p>
              <Paragraph className="whitespace-pre-wrap text-gray-600">
                {detailDrawer.requirements}
              </Paragraph>
            </div>
          </div>
        )}
      </Drawer>
    </PageTransition>
  );
}
