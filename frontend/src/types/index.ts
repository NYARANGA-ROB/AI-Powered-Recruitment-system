// ==================== Common Types ====================

export interface Result<T> {
  code: number;
  message: string;
  data: T | null;
  timestamp: number;
}

export interface Page<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

// ==================== Auth ====================

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  email: string;
  role?: 'HR' | 'INTERVIEWER';
  verificationCode: string;
}

export interface SendVerificationCodeRequest {
  email: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  userInfo: UserInfo;
}

export interface UserInfo {
  userId: number;
  username: string;
  email: string;
  role: 'ADMIN' | 'HR' | 'INTERVIEWER';
  dailyAiQuota: number;
  todayAiUsed: number;
}

// ==================== Job ====================

export interface CreateJobRequest {
  title: string;
  department?: string;
  description: string;
  requirements: string;
  requiredSkills?: string[];
  salaryMin: number;
  salaryMax: number;
  experienceMin?: number;
  experienceMax?: number;
  education?: string;
  jobType?: string;
}

export interface UpdateJobRequest extends Partial<CreateJobRequest> {
  id: number;
}

export interface JobResponse {
  id: number;
  title: string;
  department: string;
  description: string;
  requirements: string;
  requiredSkills: string[];
  salaryMin: number;
  salaryMax: number;
  salaryRange: string;
  experienceMin: number;
  experienceMax: number;
  experienceRange: string;
  education: string;
  jobType: string;
  status: JobStatus;
  statusDesc: string;
  creatorId: number;
  viewCount: number;
  createdAt: string;
  updatedAt: string;
}

export type JobStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED';

export interface JobQueryParams {
  keyword?: string;
  department?: string;
  jobType?: string;
  education?: string;
  experienceMin?: number;
  salaryMin?: number;
  status?: JobStatus;
  pageNum?: number;
  pageSize?: number;
  orderBy?: 'created_at' | 'salary_max' | 'view_count';
  orderDirection?: 'asc' | 'desc';
}

// ==================== Resume ====================

export interface ResumeUploadResponse {
  taskId: string;
  resumeId: number;
  duplicated: boolean;
  message: string;
}

export interface BatchUploadResponse {
  totalCount: number;
  successCount: number;
  failedCount: number;
  items: BatchUploadItem[];
}

export interface BatchUploadItem {
  taskId: string | null;
  resumeId: number | null;
  fileName: string;
  status: 'QUEUED' | 'DUPLICATE' | 'FAILED';
  message: string;
}

export interface TaskStatusResponse {
  status: 'PENDING' | 'PARSING' | 'COMPLETED' | 'FAILED';
  resumeId: number | null;
  candidateId: number | null;
  errorMessage: string | null;
  progress: number | null;
}

export interface Resume {
  id: number;
  userId: number;
  fileName: string;
  filePath: string;
  fileUrl: string;
  fileSize: number;
  fileHash: string;
  fileType: string;
  status: 'PARSING' | 'COMPLETED' | 'FAILED';
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}

// ==================== Candidate ====================

export interface CandidateResponse {
  id: number;
  resumeId: number;
  name: string;
  phone: string;
  email: string;
  gender: string;
  age: number;
  education: string;
  school: string;
  major: string;
  graduationYear: number;
  workYears: number;
  currentCompany: string;
  currentPosition: string;
  skills: string[];
  workExperience: WorkExperienceItem[];
  projectExperience: ProjectExperienceItem[];
  selfEvaluation: string;
  confidenceScore: number;
  aiSummary: string;
  parsedAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface WorkExperienceItem {
  company: string;
  position: string;
  startDate: string;
  endDate: string;
  description: string;
}

export interface ProjectExperienceItem {
  name: string;
  role: string;
  startDate: string;
  endDate: string;
  description: string;
  technologies: string[];
}

export interface CandidateUpdateRequest {
  name?: string;
  phone?: string;
  email?: string;
  gender?: string;
  age?: number;
  education?: string;
  school?: string;
  major?: string;
  graduationYear?: number;
  workYears?: number;
  currentCompany?: string;
  currentPosition?: string;
  skills?: string[];
  selfEvaluation?: string;
}

export interface CandidateQueryParams {
  page?: number;
  pageSize?: number;
  keyword?: string;
  education?: string;
  skill?: string;
  minWorkYears?: number;
  maxWorkYears?: number;
  currentPosition?: string;
}

// ==================== Smart Search ====================

export interface SmartSearchRequest {
  query: string;
  topK?: number;
  minScore?: number;
}

export interface SmartSearchResponse {
  query: string;
  totalMatches: number;
  candidates: MatchedCandidate[];
}

export interface MatchedCandidate {
  candidateId: number;
  name: string;
  matchScore: number;
  currentPosition: string;
  currentCompany: string;
  education: string;
  workYears: number;
  skills: string[];
  aiSummary: string;
}

// ==================== Application ====================

export interface CreateApplicationRequest {
  jobId: number;
  candidateId: number;
  hrNotes?: string;
}

export interface UpdateApplicationStatusRequest {
  status: ApplicationStatus;
  hrNotes?: string;
}

export type ApplicationStatus =
  | 'PENDING'
  | 'SCREENING'
  | 'INTERVIEW'
  | 'OFFER'
  | 'REJECTED'
  | 'WITHDRAWN';

export interface ApplicationResponse {
  id: number;
  jobId: number;
  jobTitle: string;
  candidateId: number;
  candidateName: string;
  matchScore: number;
  matchReasons: string[];
  matchCalculatedAt: string;
  status: ApplicationStatus;
  statusDesc: string;
  hrNotes: string;
  appliedAt: string;
  updatedAt: string;
}

export interface ApplicationQueryParams {
  pageNum?: number;
  pageSize?: number;
  jobId?: number;
  candidateId?: number;
  status?: ApplicationStatus;
  orderBy?: 'applied_at' | 'match_score' | 'updated_at';
  orderDirection?: 'asc' | 'desc';
}

// ==================== Interview ====================

export type InterviewType = 'PHONE' | 'VIDEO' | 'ONSITE' | 'WRITTEN_TEST';
export type InterviewStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';
export type Recommendation = 'STRONG_YES' | 'YES' | 'NEUTRAL' | 'NO' | 'STRONG_NO';

export interface ScheduleInterviewRequest {
  applicationId: number;
  interviewerId: number;
  round?: number;
  interviewType: InterviewType;
  scheduledAt: string;
  durationMinutes?: number;
}

export interface SubmitFeedbackRequest {
  score: number;
  feedback: string;
  recommendation: Recommendation;
}

export interface InterviewResponse {
  id: number;
  applicationId: number;
  interviewerId: number;
  interviewerName: string;
  round: number;
  interviewType: InterviewType;
  interviewTypeDesc: string;
  scheduledAt: string;
  durationMinutes: number;
  status: InterviewStatus;
  statusDesc: string;
  feedback: string | null;
  score: number | null;
  recommendation: Recommendation | null;
  recommendationDesc: string | null;
  createdAt: string;
  updatedAt: string;
}

// ==================== Webhook ====================

export interface WebhookCreateRequest {
  url: string;
  events: string[];
  description?: string;
}

export interface WebhookResponse {
  id: number;
  url: string;
  events: string[];
  description: string;
  enabled: boolean;
  failureCount: number;
  lastSuccessAt: string | null;
  lastFailureAt: string | null;
  createdAt: string;
  updatedAt: string;
  secretHint: string;
}

export const WEBHOOK_EVENTS = [
  'resume.uploaded',
  'resume.parse_completed',
  'resume.parse_failed',
  'candidate.created',
  'candidate.updated',
  'application.submitted',
  'application.status_changed',
  'interview.scheduled',
  'interview.completed',
  'interview.cancelled',
  'system.error',
  'system.maintenance',
] as const;

export type WebhookEventType = (typeof WEBHOOK_EVENTS)[number];

// ==================== Audit Log ====================

export interface AuditLogQueryRequest {
  userId?: number;
  module?: string;
  operation?: string;
  status?: 'SUCCESS' | 'FAILED';
  keyword?: string;
  startTime?: string;       // yyyy-MM-dd HH:mm:ss
  endTime?: string;         // yyyy-MM-dd HH:mm:ss
  pageNum?: number;
  pageSize?: number;
}

export interface AuditLogResponse {
  id: number;
  userId: number;
  username: string;
  module: string;
  operation: string;
  description: string;
  method: string;
  requestUrl: string;
  requestMethod: string;
  requestParams: string;
  requestIp: string;
  status: 'SUCCESS' | 'FAILED';
  errorMessage: string | null;
  duration: number;          // Execution duration in ms
  createdAt: string;
}

// ==================== Recruiting Analytics ====================

export interface AnalyticsQueryRequest {
  jobId?: number;
  startDate?: string;        // yyyy-MM-dd
  endDate?: string;          // yyyy-MM-dd
}

export interface FunnelStageDTO {
  stage: string;             // Stage code (ApplicationStatus)
  stageLabel: string;        // Stage display name (provided by the backend)
  count: number;
  percentage: number;        // 0-100.0
  conversionRate: number;    // 0-100.0
}

export interface RecruitmentOverviewDTO {
  totalApplications: number;
  totalCandidates: number;
  totalJobs: number;
  openJobs: number;
  interviewsScheduled: number;
  offersExtended: number;
  offerRate: number;
  avgMatchScore: number;     // 0-100
  avgDaysToOffer: number | null;
  funnel: FunnelStageDTO[];
  applicationTrend: Record<string, number>;   // { "2026-01": 42, ... }
}

// ==================== Match Score (AI Match Scoring) ====================

export interface ScoreBreakdown {
  semanticScore: number;     // Semantic similarity (30% weight)
  skillScore: number;        // Skill match (35% weight)
  experienceScore: number;   // Experience match (20% weight)
  educationScore: number;    // Education match (15% weight)
}

export interface MatchScoreResponse {
  applicationId: number;
  jobId: number;
  candidateId: number;
  totalScore: number;
  breakdown: ScoreBreakdown;
  matchReasons: string[];
  calculatedAt: string;
}
