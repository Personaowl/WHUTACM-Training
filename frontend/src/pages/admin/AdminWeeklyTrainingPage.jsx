import AtCoderAbcPanel from "../../components/admin/AtCoderAbcPanel";
import TrainingExportButton from "../../components/admin/TrainingExportButton";

export default function AdminWeeklyTrainingPage() {
  return (
    <div className="stack-y-5">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 className="m-0 text-xl font-bold tracking-tight text-text-primary">每周一赛完成情况</h2>
          <p className="m-0 mt-2 text-sm text-text-secondary">当前训练项目：AtCoder Beginner Contest</p>
        </div>
        <TrainingExportButton defaultType="weekly" />
      </header>
      <AtCoderAbcPanel />
    </div>
  );
}
