import { lazy, Suspense } from "react";
import { Routes, Route, Navigate } from "react-router-dom";
import { useAuth } from "./context/AuthContext";
import AppLayout from "./components/layout/AppLayout";

const LoginPage = lazy(() => import("./pages/LoginPage"));
const RegisterPage = lazy(() => import("./pages/RegisterPage"));
const HomePage = lazy(() => import("./pages/HomePage"));
const DailyProblemPage = lazy(() => import("./pages/DailyProblemPage"));
const WeeklyTrainingPage = lazy(() => import("./pages/WeeklyTrainingPage"));
const ContestsPage = lazy(() => import("./pages/ContestsPage"));
const LeaderboardPage = lazy(() => import("./pages/LeaderboardPage"));
const PracticePage = lazy(() => import("./pages/PracticePage"));
const ProblemListsPage = lazy(() => import("./pages/ProblemListsPage"));
const PushPage = lazy(() => import("./pages/PushPage"));
const ProfilePage = lazy(() => import("./pages/ProfilePage"));
const AccountBindingPage = lazy(() => import("./pages/AccountBindingPage"));
const MemberProfilePage = lazy(() => import("./pages/MemberProfilePage"));
const AdminDailyPage = lazy(() => import("./pages/admin/AdminDailyPage"));
const AdminTrainingDashboardPage = lazy(() => import("./pages/admin/AdminTrainingDashboardPage"));
const TrainingDashboardLayout = lazy(() => import("./pages/admin/TrainingDashboardLayout"));
const AdminWeeklyTrainingPage = lazy(() => import("./pages/admin/AdminWeeklyTrainingPage"));
const AdminWeeklyRecordsPage = lazy(() => import("./pages/admin/AdminWeeklyRecordsPage"));
const AdminUsersPage = lazy(() => import("./pages/admin/AdminUsersPage"));
const AdminPushPage = lazy(() => import("./pages/admin/AdminPushPage"));
const NotFoundPage = lazy(() => import("./pages/NotFoundPage"));

function AuthGuard({ children }) {
  const { isAuthenticated, isLoading } = useAuth();
  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-screen">
        <p className="text-text-secondary">加载中...</p>
      </div>
    );
  }
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  return children;
}

function AdminGuard({ children }) {
  const { isAdmin } = useAuth();
  if (!isAdmin) {
    return (
      <div className="flex items-center justify-center h-64">
        <p className="text-text-secondary">权限不足，需要管理员角色</p>
      </div>
    );
  }
  return children;
}

function GuestGuard({ children }) {
  const { isAuthenticated } = useAuth();
  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }
  return children;
}

export default function App() {
  return (
    <Suspense fallback={<RouteFallback />}>
      <Routes>
      {/* Public / guest routes */}
      <Route path="/login" element={<GuestGuard><LoginPage /></GuestGuard>} />
      <Route path="/register" element={<GuestGuard><RegisterPage /></GuestGuard>} />

      {/* Authenticated routes */}
      <Route element={<AuthGuard><AppLayout /></AuthGuard>}>
        <Route path="/" element={<HomePage />} />
        <Route path="/daily" element={<DailyProblemPage />} />
        <Route path="/weekly-training" element={<WeeklyTrainingPage />} />
        <Route path="/contests" element={<ContestsPage />} />
        <Route path="/leaderboard" element={<LeaderboardPage />} />
        <Route path="/practice" element={<PracticePage />} />
        <Route path="/problem-lists" element={<ProblemListsPage />} />
        <Route path="/push" element={<PushPage />} />
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="/account-binding" element={<AccountBindingPage />} />
        <Route path="/members/:id" element={<MemberProfilePage />} />

        {/* Admin routes */}
        <Route path="/admin/training" element={<AdminGuard><TrainingDashboardLayout /></AdminGuard>}>
          <Route index element={<Navigate to="daily" replace />} />
          <Route path="daily" element={<AdminTrainingDashboardPage />} />
          <Route path="weekly" element={<AdminWeeklyTrainingPage />} />
          <Route path="records" element={<AdminWeeklyRecordsPage />} />
        </Route>
        <Route path="/admin/daily" element={<AdminGuard><AdminDailyPage /></AdminGuard>} />
        <Route path="/admin/users" element={<AdminGuard><AdminUsersPage /></AdminGuard>} />
        <Route path="/admin/push" element={<AdminGuard><AdminPushPage /></AdminGuard>} />
      </Route>

      {/* 404 */}
      <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
  );
}

function RouteFallback() {
  return (
    <div className="flex min-h-[45vh] items-center justify-center" role="status">
      <span className="route-loader" aria-hidden="true" />
      <span className="sr-only">页面加载中</span>
    </div>
  );
}
