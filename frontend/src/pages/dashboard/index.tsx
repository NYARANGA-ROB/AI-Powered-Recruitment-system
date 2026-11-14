import { useEffect, useState } from 'react';
import { Button, Card, Empty, List, Statistic, Tag, Space } from 'antd';
import {
  FileTextOutlined,
  TeamOutlined,
  SolutionOutlined,
  RiseOutlined,
  EyeOutlined,
  CloudUploadOutlined,
  SearchOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import { motion } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import { jobApi, applicationApi, candidateApi } from '../../api';
import { getJobEducationLabel, getJobTypeLabel } from '../../constants';
import { useAuthStore } from '../../store/auth';
import type { JobResponse, ApplicationResponse } from '../../types';
import PageTransition from '../../components/PageTransition';
import { staggerContainer, staggerItem } from '../../components/motionVariants';

const statusColorMap: Record<string, string> = {
  PENDING: 'default',
  SCREENING: 'processing',
  INTERVIEW: 'warning',
  OFFER: 'success',
  REJECTED: 'error',
  WITHDRAWN: 'default',
};

export default function DashboardPage() {
  const userInfo = useAuthStore((s) => s.userInfo);
  const navigate = useNavigate();
  const [hotJobs, setHotJobs] = useState<JobResponse[]>([]);
  const [recentApps, setRecentApps] = useState<ApplicationResponse[]>([]);
  const [stats, setStats] = useState({ jobs: 0, candidates: 0, applications: 0 });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadDashboard();
  }, []);

  const loadDashboard = async () => {
    setLoading(true);
    try {
      const [jobsRes, hotRes, appsRes, candidatesRes] = await Promise.all([
        jobApi.list({ pageNum: 1, pageSize: 1 }),
        jobApi.hot(5),
        applicationApi.list({ pageNum: 1, pageSize: 5 }),
        candidateApi.list({ page: 1, pageSize: 1 }),
      ]);
      setStats({
        jobs: jobsRes.data.data?.total || 0,
        candidates: candidatesRes.data.data?.total || 0,
        applications: appsRes.data.data?.total || 0,
      });
      setHotJobs(hotRes.data.data || []);
      setRecentApps(appsRes.data.data?.records || []);
    } catch {
      /* handled */
    } finally {
      setLoading(false);
    }
  };

  const greetingTime = () => {
    const h = new Date().getHours();
    if (h < 6) return 'Good night';
    if (h < 12) return 'Good morning';
    if (h < 18) return 'Good afternoon';
    return 'Good evening';
  };

  const statCards = [
    {
      title: 'Total Jobs',
      value: stats.jobs,
      icon: <FileTextOutlined />,
      color: 'text-cyan-600',
      bg: 'bg-cyan-50',
    },
    {
      title: 'Candidates',
      value: stats.candidates,
      icon: <TeamOutlined />,
      color: 'text-emerald-600',
      bg: 'bg-emerald-50',
    },
    {
      title: 'Applications',
      value: stats.applications,
      icon: <SolutionOutlined />,
      color: 'text-amber-600',
      bg: 'bg-amber-50',
    },
    {
      title: 'AI Quota',
      value: `${userInfo?.todayAiUsed || 0}/${userInfo?.dailyAiQuota || 0}`,
      icon: <RiseOutlined />,
      color: 'text-violet-600',
      bg: 'bg-violet-50',
    },
  ];

  const quickActions = [
    { icon: <FileTextOutlined />, label: 'Post a Job', desc: 'Create a new job', path: '/jobs' },
    { icon: <CloudUploadOutlined />, label: 'Upload Resume', desc: 'AI-powered parsing', path: '/resumes' },
    { icon: <SearchOutlined />, label: 'Smart Search', desc: 'RAG semantic matching', path: '/candidates/search' },
    { icon: <SettingOutlined />, label: 'System Settings', desc: 'Webhook configuration', path: '/settings' },
  ];

  return (
    <PageTransition>
      <div className="mb-6">
        <Card
          bordered={false}
          className="!border-0 !shadow-sm overflow-hidden"
          styles={{ body: { padding: '24px 24px 18px' } }}
        >
          <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <div className="inline-flex items-center gap-2 rounded-full bg-cyan-50 px-2.5 py-1 text-xs font-medium text-cyan-700 border border-cyan-100">
                <span className="h-2 w-2 rounded-full bg-emerald-500" />
                Recruiting operations live
              </div>
              <h1 className="mt-3 text-2xl font-bold tracking-tight text-gray-900 md:text-3xl">
                {greetingTime()}, {userInfo?.username}
              </h1>
              <p className="mt-1 text-sm text-gray-500 md:text-base">
                This is your recruiting workspace — track hiring progress, pipeline health, and AI-assisted candidate flow in real time.
              </p>
            </div>

            <div className="flex items-center gap-3">
              <Button type="primary" onClick={() => navigate('/jobs')}>
                Post a Role
              </Button>
              <Button onClick={() => navigate('/candidates/search')}>Smart Search</Button>
            </div>
          </div>
        </Card>
      </div>

      <motion.div
        variants={staggerContainer}
        initial="initial"
        animate="animate"
        className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4 mb-6"
      >
        {statCards.map((item) => (
          <motion.div key={item.title} variants={staggerItem}>
            <Card bordered={false} className="!border-0 !shadow-sm hover:!shadow-md transition-shadow" styles={{ body: { padding: '20px' } }}>
              <div className="flex items-center justify-between">
                <div>
                  <p className="mb-2 text-sm text-gray-500">{item.title}</p>
                  <div className="text-3xl font-bold text-gray-900">
                    {typeof item.value === 'number' ? (
                      <Statistic
                        value={item.value}
                        valueStyle={{
                          fontSize: '28px',
                          fontWeight: 700,
                          color: '#111827',
                          lineHeight: 1,
                        }}
                      />
                    ) : (
                      item.value
                    )}
                  </div>
                </div>
                <div className={`flex h-14 w-14 items-center justify-center rounded-xl ${item.bg} text-2xl ${item.color}`}>
                  {item.icon}
                </div>
              </div>
            </Card>
          </motion.div>
        ))}
      </motion.div>

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2, type: 'spring', stiffness: 200, damping: 30 }}
        >
          <Card bordered={false} className="!border-0 !shadow-sm h-full" title={<span className="font-medium text-gray-700">Popular Jobs</span>} loading={loading}>
            {hotJobs.length ? (
              <List
                dataSource={hotJobs}
                renderItem={(job) => (
                  <List.Item
                    className="!border-b-0"
                    extra={
                      <Space size={4}>
                        <EyeOutlined className="text-gray-400 text-xs" />
                        <span className="text-gray-400 text-sm">{job.viewCount}</span>
                      </Space>
                    }
                  >
                    <List.Item.Meta
                      title={
                        <Space>
                          <span className="font-medium text-gray-900">{job.title}</span>
                          <Tag className="!text-xs !border-cyan-100 !bg-cyan-50 !text-cyan-600">{job.salaryRange}</Tag>
                        </Space>
                      }
                      description={
                        <span className="text-gray-400 text-sm">
                          {job.department || '-'} · {getJobTypeLabel(job.jobType, 'Full-time')} · {getJobEducationLabel(job.education, 'Any')}
                        </span>
                      }
                    />
                  </List.Item>
                )}
              />
            ) : (
              <Empty description="No hot jobs yet" imageStyle={{ height: 84 }} />
            )}
          </Card>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.28, type: 'spring', stiffness: 200, damping: 30 }}
        >
          <Card bordered={false} className="!border-0 !shadow-sm h-full" title={<span className="font-medium text-gray-700">Latest Applications</span>} loading={loading}>
            {recentApps.length ? (
              <List
                dataSource={recentApps}
                renderItem={(app) => (
                  <List.Item className="!border-b-0">
                    <List.Item.Meta
                      title={
                        <div className="flex items-center justify-between w-full pr-4">
                          <span className="font-medium text-gray-900">{app.candidateName || '-'}</span>
                          <Tag color={statusColorMap[app.status]} className="!text-xs">
                            {app.statusDesc}
                          </Tag>
                        </div>
                      }
                      description={
                        <div className="flex items-center justify-between pr-4">
                          <span className="text-gray-400">{app.jobTitle || '-'}</span>
                          {app.matchScore && (
                            <span className="text-cyan-600 font-medium text-sm">
                              {app.matchScore}% match
                            </span>
                          )}
                        </div>
                      }
                    />
                  </List.Item>
                )}
              />
            ) : (
              <Empty description="No recent applications" imageStyle={{ height: 84 }} />
            )}
          </Card>
        </motion.div>
      </div>

      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.35, type: 'spring', stiffness: 200, damping: 30 }}
        className="mt-5"
      >
        <Card bordered={false} className="!border-0 !shadow-sm" title={<span className="font-medium text-gray-700">Quick Actions</span>}>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            {quickActions.map((item) => (
              <button
                key={item.label}
                onClick={() => navigate(item.path)}
                className="group rounded-xl border border-gray-100 bg-gray-50 p-4 text-left transition-all hover:border-cyan-200 hover:bg-cyan-50"
              >
                <div className="mb-2 flex h-10 w-10 items-center justify-center rounded-lg bg-white text-xl text-cyan-600 shadow-sm transition-transform group-hover:scale-110">
                  {item.icon}
                </div>
                <div className="text-sm font-medium text-gray-700">{item.label}</div>
                <div className="mt-1 text-xs text-gray-400">{item.desc}</div>
              </button>
            ))}
          </div>
        </Card>
      </motion.div>
    </PageTransition>
  );
}
