import { useCallback, useEffect, useRef, useState } from "react";
import { getWeeklyTraining } from "../api/weeklyTraining";

export default function useWeeklyTraining(admin = false) {
  const [week, setSelectedWeek] = useState("");
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const sequence = useRef(0);

  const reload = useCallback(async () => {
    const request = ++sequence.current;
    setLoading(true);
    setError("");
    try {
      const response = await getWeeklyTraining(week, admin);
      if (request !== sequence.current) return;
      if (response.code !== 200)
        throw new Error(response.message || "周训练记录加载失败");
      setReport(response.data);
    } catch (failure) {
      if (request === sequence.current)
        setError(failure.message || "周训练记录加载失败，请重试");
    } finally {
      if (request === sequence.current) setLoading(false);
    }
  }, [week, admin]);

  useEffect(() => {
    reload();
    return () => {
      sequence.current += 1;
    };
  }, [reload]);

  function updateReport(updated) {
    setReport((current) => {
      if (!current || current.weekStart !== updated.weekStart) return current;
      const changed = new Map(
        updated.members.map((member) => [member.userId, member]),
      );
      return {
        ...current,
        members: current.members.map(
          (member) => changed.get(member.userId) || member,
        ),
      };
    });
  }

  function setWeek(next) {
    if (next === week) reload();
    else setSelectedWeek(next);
  }

  return { report, loading, error, setWeek, reload, updateReport };
}
