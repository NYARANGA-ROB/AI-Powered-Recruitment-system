import { useEffect, useState } from 'react';
import {
  Card,
  Table,
  Button,
  Space,
  Tag,
  Typography,
  Modal,
  Form,
  Input,
  message,
  Popconfirm,
  Badge,
  Checkbox,
} from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  ApiOutlined,
  SendOutlined,
  LoadingOutlined,
  LinkOutlined,
} from '@ant-design/icons';
import { motion } from 'framer-motion';
import { webhookApi } from '../../api';
import type { WebhookResponse, WebhookEventType } from '../../types';
import PageTransition from '../../components/PageTransition';
import { staggerContainer, staggerItem } from '../../components/motionVariants';

const { Text } = Typography;

const eventCategoryMap: Record<
  string,
  { label: string; color: string; events: WebhookEventType[] }
> = {
  resume: {
    label: 'Resume',
    color: 'blue',
    events: ['resume.uploaded', 'resume.parse_completed', 'resume.parse_failed'],
  },
  candidate: {
    label: 'Candidate',
    color: 'green',
    events: ['candidate.created', 'candidate.updated'],
  },
  application: {
    label: 'Application',
    color: 'purple',
    events: ['application.submitted', 'application.status_changed'],
  },
  interview: {
    label: 'Interview',
    color: 'orange',
    events: ['interview.scheduled', 'interview.completed', 'interview.cancelled'],
  },
  system: {
    label: 'System',
    color: 'red',
    events: ['system.error', 'system.maintenance'],
  },
};

export default function SettingsPage() {
  const [webhooks, setWebhooks] = useState<WebhookResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [createModal, setCreateModal] = useState(false);
  const [testingId, setTestingId] = useState<number | null>(null);
  const [form] = Form.useForm();

  useEffect(() => {
    loadWebhooks();
  }, []);

  const loadWebhooks = async () => {
    setLoading(true);
    try {
      const { data } = await webhookApi.list();
      setWebhooks(data.data || []);
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = async () => {
    const values = await form.validateFields();
    await webhookApi.create(values);
    message.success('Webhook created successfully');
    setCreateModal(false);
    form.resetFields();
    loadWebhooks();
  };

  const handleDelete = async (id: number) => {
    await webhookApi.delete(id);
    message.success('Deleted');
    loadWebhooks();
  };

  const handleTest = async (id: number) => {
    setTestingId(id);
    try {
      await webhookApi.test(id);
      message.success('Test message sent');
    } catch {
      message.error('Test send failed');
    } finally {
      setTestingId(null);
    }
  };

  const columns = [
    {
      title: 'URL',
      dataIndex: 'url',
      width: 280,
      render: (url: string) => (
        <span className="flex items-center gap-2">
          <LinkOutlined className="text-cyan-600" />
          <Text copyable ellipsis style={{ maxWidth: 240 }}>
            {url}
          </Text>
        </span>
      ),
    },
    {
      title: 'Event Types',
      dataIndex: 'events',
      width: 240,
      render: (events: string[]) => (
        <Space size={4} wrap>
          {events?.slice(0, 3).map((e) => {
            const cat = Object.values(eventCategoryMap).find((c) =>
              c.events.includes(e as WebhookEventType)
            );
            return <Tag key={e} color={cat?.color || 'default'}>
              {e}
            </Tag>;
          })}
          {events?.length > 3 && <Tag>+{events.length - 3}</Tag>}
        </Space>
      ),
    },
    {
      title: 'Signing Secret',
      dataIndex: 'secretHint',
      width: 100,
      render: (v: string) =>
        v ? (
          <Text code>{v}</Text>
        ) : (
          <span className="text-gray-400">Not set</span>
        ),
    },
    {
      title: 'Status',
      dataIndex: 'enabled',
      width: 80,
      render: (active: boolean) =>
        active !== false ? (
          <Badge status="success" text="Active" />
        ) : (
          <Badge status="default" text="Disabled" />
        ),
    },
    {
      title: 'Actions',
      width: 140,
      render: (_: unknown, record: WebhookResponse) => (
        <Space size={4}>
          <Button
            type="link"
            size="small"
            icon={testingId === record.id ? <LoadingOutlined /> : <SendOutlined />}
            loading={testingId === record.id}
            onClick={() => handleTest(record.id)}
          >
            Test
          </Button>
          <Popconfirm title="Are you sure you want to delete this webhook?" onConfirm={() => handleDelete(record.id)}>
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
      <h2 className="text-xl font-semibold text-gray-900 mb-6">System Settings</h2>

      <motion.div variants={staggerContainer} initial="initial" animate="animate">
        {/* Webhook management */}
        <motion.div variants={staggerItem}>
          <Card
            bordered={false}
            title={
              <span className="flex items-center gap-2">
                <ApiOutlined className="text-cyan-600" />
                <span>Webhook Configuration</span>
              </span>
            }
            extra={
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => setCreateModal(true)}
              >
                New Webhook
              </Button>
            }
          >
            <Table
              rowKey="id"
              columns={columns}
              dataSource={webhooks}
              loading={loading}
              scroll={{ x: true }}
              pagination={false}
            />
          </Card>
        </motion.div>
      </motion.div>

      {/* Create webhook modal */}
      <Modal
        title="New Webhook"
        open={createModal}
        onCancel={() => setCreateModal(false)}
        onOk={handleCreate}
        okText="Create"
        width={580}
      >
        <Form form={form} layout="vertical" className="mt-4">
          <Form.Item
            name="url"
            label="Webhook URL"
            rules={[
              { required: true, message: 'Please enter a URL' },
              { type: 'url', message: 'Please enter a valid URL' },
            ]}
          >
            <Input
              prefix={<LinkOutlined />}
              placeholder="https://example.com/webhook"
            />
          </Form.Item>

          <Form.Item name="secret" label="Signing Secret (HMAC-SHA256)">
            <Input.Password placeholder="Optional, used to verify message signatures" />
          </Form.Item>

          <Form.Item
            name="events"
            label="Subscribed Events"
            rules={[{ required: true, message: 'Select at least one event' }]}
          >
            <Checkbox.Group className="w-full">
              {Object.entries(eventCategoryMap).map(([catKey, catData]) => (
                <div key={catKey} className="mb-4">
                  <span className="font-semibold">
                    <Tag color={catData.color} className="mr-2">
                      {catData.label}
                    </Tag>
                  </span>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-2 mt-2">
                    {catData.events.map((event) => (
                      <Checkbox key={event} value={event} className="mb-1">
                        <span className="text-[13px]">{event}</span>
                      </Checkbox>
                    ))}
                  </div>
                </div>
              ))}
            </Checkbox.Group>
          </Form.Item>
        </Form>
      </Modal>
    </PageTransition>
  );
}
