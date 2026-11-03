import { useCallback, useEffect, useState } from 'react';
import {
  Table,
  Card,
  Space,
  Tag,
  Input,
  Select,
  Typography,
  Button,
  Drawer,
  Descriptions,
  Form,
  Modal,
  message,
  Popconfirm,
  Divider,
  InputNumber,
} from 'antd';
import {
  SearchOutlined,
  UserOutlined,
  EditOutlined,
  DeleteOutlined,
  EyeOutlined,
  PhoneOutlined,
  MailOutlined,
} from '@ant-design/icons';
import { motion } from 'framer-motion';
import { candidateApi } from '../../api';
import type { CandidateResponse, CandidateQueryParams, WorkExperienceItem } from '../../types';
import PageTransition from '../../components/PageTransition';
import { staggerContainer, staggerItem } from '../../components/motionVariants';

const { Text, Paragraph } = Typography;

export default function CandidatesPage() {
  const [candidates, setCandidates] = useState<CandidateResponse[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState<CandidateQueryParams>({ page: 1, pageSize: 10 });
  const [detailDrawer, setDetailDrawer] = useState<CandidateResponse | null>(null);
  const [editModal, setEditModal] = useState<CandidateResponse | null>(null);
  const [form] = Form.useForm();

  const loadCandidates = useCallback(async () => {
    setLoading(true);
    try {
      const { data } = await candidateApi.list(query);
      setCandidates(data.data?.records || []);
      setTotal(data.data?.total || 0);
    } finally {
      setLoading(false);
    }
  }, [query]);

  useEffect(() => {
    loadCandidates();
  }, [loadCandidates]);

  const handleEdit = (record: CandidateResponse) => {
    setEditModal(record);
    form.setFieldsValue({ ...record, skills: record.skills?.join(', ') });
  };

  const handleUpdate = async () => {
    const values = await form.validateFields();
    const skills = values.skills
      ? values.skills.split(/[,，]/).map((s: string) => s.trim()).filter(Boolean)
      : undefined;
    await candidateApi.update(editModal!.id, { ...values, skills });
    message.success('Updated successfully');
    setEditModal(null);
    loadCandidates();
  };

  const handleDelete = async (id: number) => {
    await candidateApi.delete(id);
    message.success('Deleted');
    loadCandidates();
  };

  const columns = [
    {
      title: 'Name',
      dataIndex: 'name',
      width: 120,
      render: (name: string, record: CandidateResponse) => (
        <a
          className="text-gray-900 hover:text-cyan-600 transition-colors font-medium cursor-pointer"
          onClick={() => setDetailDrawer(record)}
        >
          <Space>
            <UserOutlined />
            {name}
          </Space>
        </a>
      ),
    },
    {
      title: 'Email',
      dataIndex: 'email',
      width: 160,
      render: (v: string) => <span className="text-gray-500">{v || '-'}</span>,
    },
    {
      title: 'Phone',
      dataIndex: 'phone',
      width: 140,
      render: (v: string) => <span className="text-gray-500">{v || '-'}</span>,
    },
    { title: 'Education', dataIndex: 'education', width: 90, render: (v: string) => v || '-' },
    {
      title: 'Years of Experience',
      dataIndex: 'workYears',
      width: 90,
      render: (v: number) => (v !== undefined && v !== null ? `${v} yrs` : '-'),
    },
    {
      title: 'Skills',
      dataIndex: 'skills',
      width: 200,
      render: (skills: string[]) =>
        skills?.length ? (
          <div className="flex flex-wrap gap-1">
            {skills.slice(0, 3).map((s) => (
              <Tag key={s} color="blue">
                {s}
              </Tag>
            ))}
            {skills.length > 3 && <Tag>+{skills.length - 3}</Tag>}
          </div>
        ) : (
          '-'
        ),
    },
    { title: 'Created At', dataIndex: 'createdAt', width: 160 },
    {
      title: 'Actions',
      width: 160,
      render: (_: unknown, record: CandidateResponse) => (
        <Space size={4}>
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => setDetailDrawer(record)}
          >
            View
          </Button>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
          >
            Edit
          </Button>
          <Popconfirm title="Are you sure you want to delete this candidate?" onConfirm={() => handleDelete(record.id)}>
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
      <h2 className="text-xl font-semibold text-gray-900 mb-6">Candidate Management</h2>

      <motion.div variants={staggerContainer} initial="initial" animate="animate">
        {/* Search filters */}
        <motion.div variants={staggerItem}>
          <Card bordered={false} className="mb-4">
            <div className="grid grid-cols-1 md:grid-cols-12 gap-3">
              <div className="md:col-span-3">
                <Input
                  placeholder="Search by name"
                  prefix={<SearchOutlined className="text-gray-400" />}
                  allowClear
                  onChange={(e) => setQuery((q) => ({ ...q, keyword: e.target.value, page: 1 }))}
                />
              </div>
              <div className="md:col-span-2">
                <Select
                  placeholder="Education"
                  allowClear
                  className="w-full"
                  onChange={(v) => setQuery((q) => ({ ...q, education: v, page: 1 }))}
                  options={[
                    { value: '大专', label: 'Associate' },
                    { value: '本科', label: "Bachelor's" },
                    { value: '硕士', label: "Master's" },
                    { value: '博士', label: 'PhD' },
                  ]}
                />
              </div>
              <div className="md:col-span-3">
                <Input
                  placeholder="Skill keyword"
                  allowClear
                  onChange={(e) =>
                    setQuery((q) => ({ ...q, skill: e.target.value, page: 1 }))
                  }
                />
              </div>
              <div className="md:col-span-2">
                <InputNumber
                  placeholder="Min years of experience"
                  min={0}
                  className="w-full"
                  onChange={(v) =>
                    setQuery((q) => ({ ...q, minWorkYears: v ?? undefined, page: 1 }))
                  }
                />
              </div>
              <div className="md:col-span-2">
                <InputNumber
                  placeholder="Max years of experience"
                  min={0}
                  className="w-full"
                  onChange={(v) =>
                    setQuery((q) => ({ ...q, maxWorkYears: v ?? undefined, page: 1 }))
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
              dataSource={candidates}
              loading={loading}
              scroll={{ x: true }}
              pagination={{
                current: query.page,
                pageSize: query.pageSize,
                total,
                showSizeChanger: true,
                showTotal: (t) => `${t} records in total`,
                onChange: (p, s) => setQuery((q) => ({ ...q, page: p, pageSize: s })),
              }}
            />
          </Card>
        </motion.div>
      </motion.div>

      {/* Detail drawer */}
      <Drawer
        title={detailDrawer ? `${detailDrawer.name} - Candidate Details` : ''}
        open={!!detailDrawer}
        onClose={() => setDetailDrawer(null)}
        width={560}
      >
        {detailDrawer && (
          <div className="space-y-6">
            {/* Header info card */}
            <div className="flex items-center gap-4 p-4 rounded-xl bg-gradient-to-br from-gray-50 to-cyan-50/50">
              <div className="w-14 h-14 rounded-full bg-gray-900 text-white flex items-center justify-center text-xl font-semibold shrink-0">
                {detailDrawer.name?.charAt(0)}
              </div>
              <div>
                <h3 className="text-lg font-semibold text-gray-900 m-0">
                  {detailDrawer.name}
                </h3>
                <div className="flex flex-wrap gap-4 mt-1 text-gray-500 text-sm">
                  {detailDrawer.phone && (
                    <span>
                      <PhoneOutlined className="mr-1" />
                      {detailDrawer.phone}
                    </span>
                  )}
                  {detailDrawer.email && (
                    <span>
                      <MailOutlined className="mr-1" />
                      {detailDrawer.email}
                    </span>
                  )}
                </div>
              </div>
            </div>

            <Descriptions column={2} size="small">
              <Descriptions.Item label="Education">
                {detailDrawer.education || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="Years of Experience">
                {detailDrawer.workYears ?? '-'} yrs
              </Descriptions.Item>
              <Descriptions.Item label="Created At">
                {detailDrawer.createdAt}
              </Descriptions.Item>
              <Descriptions.Item label="Updated At">
                {detailDrawer.updatedAt}
              </Descriptions.Item>
            </Descriptions>

            {detailDrawer.skills?.length > 0 && (
              <>
                <Divider className="my-3" />
                <div>
                  <p className="font-semibold text-gray-900 mb-2">Skill Tags</p>
                  <div className="flex flex-wrap gap-1.5">
                    {detailDrawer.skills.map((s) => (
                      <Tag color="blue" key={s}>
                        {s}
                      </Tag>
                    ))}
                  </div>
                </div>
              </>
            )}

            {detailDrawer.workExperience?.length > 0 && (
              <>
                <Divider className="my-3" />
                <div>
                  <p className="font-semibold text-gray-900 mb-2">Work Experience</p>
                  {detailDrawer.workExperience.map(
                    (exp: WorkExperienceItem, idx: number) => (
                      <div
                        key={idx}
                        className="mt-3 p-3 bg-gray-50 rounded-lg"
                      >
                        <Text strong>{exp.company}</Text>
                        <Text type="secondary" className="ml-2">
                          {exp.position}
                        </Text>
                        <br />
                        <Text type="secondary" className="text-xs">
                          {exp.startDate} - {exp.endDate || 'Present'}
                        </Text>
                        {exp.description && (
                          <Paragraph className="mt-1 mb-0 text-sm text-gray-600">
                            {exp.description}
                          </Paragraph>
                        )}
                      </div>
                    )
                  )}
                </div>
              </>
            )}

            {detailDrawer.aiSummary && (
              <>
                <Divider className="my-3" />
                <div>
                  <p className="font-semibold text-gray-900 mb-2">AI Summary</p>
                  <Paragraph className="text-gray-600">
                    {detailDrawer.aiSummary}
                  </Paragraph>
                </div>
              </>
            )}
          </div>
        )}
      </Drawer>

      {/* Edit modal */}
      <Modal
        title="Edit Candidate"
        open={!!editModal}
        onCancel={() => setEditModal(null)}
        onOk={handleUpdate}
        okText="Save"
        width={560}
      >
        <Form form={form} layout="vertical" className="mt-4">
          <div className="grid grid-cols-2 gap-4">
            <Form.Item name="name" label="Name" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="phone" label="Phone">
              <Input />
            </Form.Item>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Form.Item name="email" label="Email">
              <Input />
            </Form.Item>
            <Form.Item name="education" label="Education">
              <Select
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
          <Form.Item name="workYears" label="Years of Experience">
            <InputNumber min={0} className="w-full" />
          </Form.Item>
          <Form.Item name="skills" label="Skill Tags (comma-separated)">
            <Input placeholder="e.g. Java, Python, React" />
          </Form.Item>
          <Form.Item name="summary" label="Personal Summary">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </PageTransition>
  );
}
