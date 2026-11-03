import { useState } from 'react';
import {
  Card,
  Input,
  Button,
  Typography,
  Tag,
  Spin,
  Empty,
  Progress,
} from 'antd';
import {
  SearchOutlined,
  UserOutlined,
  TrophyOutlined,
  BookOutlined,
  BulbOutlined,
  RobotOutlined,
} from '@ant-design/icons';
import { motion } from 'framer-motion';
import { candidateApi } from '../../api';
import type { SmartSearchResponse, MatchedCandidate } from '../../types';
import PageTransition from '../../components/PageTransition';
import { staggerContainer, staggerItem } from '../../components/motionVariants';

const { Text } = Typography;
const { TextArea } = Input;

export default function SmartSearchPage() {
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<SmartSearchResponse | null>(null);

  const handleSearch = async () => {
    if (!query.trim()) return;
    setLoading(true);
    setResult(null);
    try {
      const { data } = await candidateApi.smartSearch({ query, topK: 10 });
      setResult(data.data || null);
    } finally {
      setLoading(false);
    }
  };

  return (
    <PageTransition>
      <h2 className="text-xl font-semibold text-zen-ink mb-1">Smart Candidate Search</h2>
      <p className="text-zen-muted mb-6">
        Describe your needs in natural language, and AI will recommend the best-fit candidates through semantic matching
      </p>

      {/* Search area */}
      <Card bordered={false} className="mb-6 bg-gradient-to-br from-zen-light to-blue-50/60">
        <div className="max-w-[800px] mx-auto">
          <div className="flex gap-3 items-start">
            <div className="flex-1">
              <TextArea
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="e.g. Find a candidate with 5+ years of Java development experience, familiar with Spring Boot and microservices architecture, with large-scale e-commerce project background"
                autoSize={{ minRows: 2, maxRows: 4 }}
                className="!rounded-xl !text-[15px]"
                onPressEnter={(e) => { if (!e.shiftKey) { e.preventDefault(); handleSearch(); } }}
              />
              <p className="text-zen-muted text-xs mt-1">
                Tip: The more detailed your description, the more precise the matches. Supports multi-dimensional search across skills, experience, education, and project history.
              </p>
            </div>
            <Button
              type="primary"
              size="large"
              icon={<SearchOutlined />}
              loading={loading}
              onClick={handleSearch}
              className="!rounded-xl !h-14 !px-8"
            >
              Search
            </Button>
          </div>

          {/* Quick examples */}
          <div className="flex flex-wrap items-center gap-2 mt-3">
            <span className="text-zen-muted text-xs">Try:</span>
            {[
              'Senior Java engineer with 5+ years of experience',
              'Full-stack developer familiar with React and Node.js',
              'Data analyst proficient in Python and SQL',
              'Product manager with B2B SaaS experience',
            ].map((example) => (
              <Tag
                key={example}
                className="cursor-pointer !rounded-md hover:!border-accent transition-colors"
                onClick={() => setQuery(example)}
              >
                {example}
              </Tag>
            ))}
          </div>
        </div>
      </Card>

      {/* Loading */}
      {loading && (
        <div className="text-center py-16">
          <Spin size="large" />
          <p className="mt-4 text-zen-muted">
            <RobotOutlined className="mr-1" /> AI is matching the best candidates for you...
          </p>
        </div>
      )}

      {/* Search results */}
      {!loading && result && (
        <>
          <p className="text-zen-muted mb-4">
            Found <span className="font-semibold text-zen-ink">{result.totalMatches || 0}</span> matching candidates
          </p>

          {result.candidates?.length > 0 ? (
            <motion.div
              className="grid grid-cols-2 gap-4"
              variants={staggerContainer}
              initial="initial"
              animate="animate"
            >
              {result.candidates.map((candidate, idx) => (
                <motion.div key={candidate.candidateId} variants={staggerItem}>
                  <CandidateCard candidate={candidate} rank={idx + 1} />
                </motion.div>
              ))}
            </motion.div>
          ) : (
            <Empty description="No matching candidates found, please refine your search description" />
          )}
        </>
      )}

      {/* Initial state */}
      {!loading && !result && (
        <div className="text-center py-16">
          <RobotOutlined className="text-6xl text-zen-border" />
          <p className="mt-4 text-zen-muted text-base">Enter a description to start smart search</p>
        </div>
      )}
    </PageTransition>
  );
}

function CandidateCard({ candidate, rank }: { candidate: MatchedCandidate; rank: number }) {
  const getScoreColor = (score: number) => {
    if (score >= 80) return '#10b981';
    if (score >= 60) return '#f59e0b';
    return '#94a3b8';
  };
  const scorePercent = Math.round(candidate.matchScore);

  return (
    <Card bordered={false} hoverable className="h-full !border !border-zen-border">
      <div className="flex gap-4">
        {/* Rank */}
        <div
          className={`w-9 h-9 rounded-full flex items-center justify-center font-bold text-sm shrink-0 ${
            rank <= 3 ? 'bg-zen-ink text-white' : 'bg-zen-light text-zen-muted'
          }`}
        >
          {rank}
        </div>

        <div className="flex-1 min-w-0">
          {/* Header */}
          <div className="flex justify-between items-start">
            <div>
              <Text strong className="text-base">
                <UserOutlined className="mr-1.5" />{candidate.name}
              </Text>
              <div className="mt-1 text-zen-muted text-sm">
                {candidate.education && <span><BookOutlined className="mr-1" />{candidate.education}</span>}
                {candidate.workYears !== undefined && candidate.workYears !== null && (
                  <span className="ml-3"><TrophyOutlined className="mr-1" />{candidate.workYears} yrs experience</span>
                )}
              </div>
            </div>
            <div className="text-center">
              <Progress
                type="circle"
                size={50}
                percent={scorePercent}
                strokeColor={getScoreColor(candidate.matchScore)}
                format={() => `${scorePercent}`}
              />
              <div className="text-[11px] text-zen-muted mt-0.5">Match</div>
            </div>
          </div>

          {/* Current position */}
          {(candidate.currentPosition || candidate.currentCompany) && (
            <p className="mt-2 text-zen-muted text-xs">
              {candidate.currentPosition}{candidate.currentCompany && ` @ ${candidate.currentCompany}`}
            </p>
          )}

          {/* Skills */}
          {candidate.skills?.length > 0 && (
            <div className="mt-2.5 flex flex-wrap gap-1">
              {candidate.skills.slice(0, 6).map((s) => (
                <Tag key={s} color="blue" className="!rounded">{s}</Tag>
              ))}
              {candidate.skills.length > 6 && <Tag>+{candidate.skills.length - 6}</Tag>}
            </div>
          )}

          {/* AI summary */}
          {candidate.aiSummary && (
            <div className="mt-2.5 p-2 px-3 rounded-lg bg-accent-light/30 text-sm text-zen-secondary">
              <BulbOutlined className="text-accent mr-1.5" />
              {candidate.aiSummary}
            </div>
          )}
        </div>
      </div>
    </Card>
  );
}
