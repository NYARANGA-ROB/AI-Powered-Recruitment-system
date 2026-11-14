import type { CandidateEducationLevel, EducationLevel, JobType } from './types';

export const JOB_EDUCATION_OPTIONS: { value: EducationLevel; label: string }[] = [
  { value: 'UNSPECIFIED', label: 'Any' },
  { value: 'HIGH_SCHOOL', label: 'High School' },
  { value: 'ASSOCIATE', label: 'Associate' },
  { value: 'BACHELOR', label: "Bachelor's" },
  { value: 'MASTER', label: "Master's" },
  { value: 'DOCTORATE', label: 'Doctorate' },
];

export const CANDIDATE_EDUCATION_OPTIONS: { value: CandidateEducationLevel; label: string }[] = [
  { value: 'UNSPECIFIED', label: 'Not specified' },
  { value: 'UNKNOWN', label: 'Unknown' },
  { value: 'HIGH_SCHOOL', label: 'High School' },
  { value: 'ASSOCIATE', label: 'Associate' },
  { value: 'BACHELOR', label: "Bachelor's" },
  { value: 'MASTER', label: "Master's" },
  { value: 'DOCTORATE', label: 'Doctorate' },
];

export const JOB_TYPE_OPTIONS: { value: JobType; label: string }[] = [
  { value: 'FULL_TIME', label: 'Full-time' },
  { value: 'PART_TIME', label: 'Part-time' },
  { value: 'INTERN', label: 'Internship' },
];

const educationLabels: Record<string, string> = {
  UNSPECIFIED: 'Not specified',
  UNKNOWN: 'Unknown',
  HIGH_SCHOOL: 'High School',
  ASSOCIATE: 'Associate',
  BACHELOR: "Bachelor's",
  "BACHELOR'S": "Bachelor's",
  MASTER: "Master's",
  "MASTER'S": "Master's",
  DOCTORATE: 'Doctorate',
  PHD: 'PhD',
};

const jobTypeLabels: Record<string, string> = {
  FULL_TIME: 'Full-time',
  PART_TIME: 'Part-time',
  INTERN: 'Internship',
};

export function getEducationLabel(value: string | null | undefined, fallback = '-'): string {
  if (!value) {
    return fallback;
  }
  return educationLabels[value.trim().toUpperCase()] ?? value;
}

export function getJobEducationLabel(value: string | null | undefined, fallback = '-'): string {
  if (!value) {
    return fallback;
  }
  if (value.trim().toUpperCase() === 'UNSPECIFIED') {
    return 'Any';
  }
  return getEducationLabel(value, fallback);
}

export function getJobTypeLabel(value: string | null | undefined, fallback = '-'): string {
  if (!value) {
    return fallback;
  }
  return jobTypeLabels[value.trim().toUpperCase()] ?? value;
}
