import { useEffect, useMemo, useRef, useState } from "react";
import {
  addProblemListItem,
  createProblemList,
  deleteProblemList,
  deleteProblemListItem,
  getProblemList,
  listProblemLists,
  updateProblemList,
  updateProblemListItem,
} from "../api/problemList.js";
import { canAddProblem, visibleProblemLists } from "../utils/problemListPermissions.js";

const EMPTY_LIST_FORM = { name: "", description: "", shared: false };
const EMPTY_PROBLEM_FORM = {
  title: "",
  link: "",
  problemKey: "",
  rating: "",
  tags: "",
  note: "",
};

// Keeps requests and editor state scoped to the current personal/shared workspace.
export default function useProblemLists(sharedOnly = false) {
  const [lists, setLists] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const detailRequest = useRef(0);
  const scopeToken = useRef(0);
  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [listDialogOpen, setListDialogOpen] = useState(false);
  const [editingList, setEditingList] = useState(null);
  const [listForm, setListForm] = useState(EMPTY_LIST_FORM);
  const [problemDialogOpen, setProblemDialogOpen] = useState(false);
  const [editingProblem, setEditingProblem] = useState(null);
  const [problemForm, setProblemForm] = useState(EMPTY_PROBLEM_FORM);

  const myLists = useMemo(() => lists.filter((item) => sharedOnly ? item.collaborative : item.owner), [lists, sharedOnly]);
  const sharedLists = useMemo(() => lists.filter((item) => item.shared && !item.collaborative), [lists]);

  async function loadLists(preferredId) {
    const scope = scopeToken.current;
    const response = await listProblemLists();
    if (scope !== scopeToken.current) return null;
    const data = visibleProblemLists(requireData(response), sharedOnly);
    setLists(data || []);
    const candidate = preferredId ?? selectedId;
    const nextId = data.some((item) => item.id === candidate)
      ? candidate
      : data[0]?.id ?? null;
    setSelectedId(nextId);
    return nextId;
  }

  async function loadDetail(id) {
    const request = ++detailRequest.current;
    if (!id) {
      setDetail(null);
      setDetailLoading(false);
      return;
    }
    setDetailLoading(true);
    try {
      const response = await getProblemList(id);
      if (request === detailRequest.current) setDetail(requireData(response));
    } catch (requestError) {
      if (request === detailRequest.current) {
        setError(requestError.message || "题单加载失败");
        setDetail(null);
      }
    } finally {
      if (request === detailRequest.current) setDetailLoading(false);
    }
  }

  useEffect(() => {
    let active = true;
    scopeToken.current++;
    detailRequest.current++;
    setLoading(true);
    setLists([]);
    setSelectedId(null);
    setDetail(null);
    setError("");
    setSaving(false);
    setListDialogOpen(false);
    setProblemDialogOpen(false);
    async function initialize() {
      try {
        const response = await listProblemLists();
        if (!active) return;
        const data = visibleProblemLists(requireData(response), sharedOnly);
        setLists(data);
        setSelectedId(data[0]?.id ?? null);
      } catch (requestError) {
        if (active) setError(requestError.message || "题单加载失败");
      } finally {
        if (active) setLoading(false);
      }
    }
    initialize();
    return () => { active = false; scopeToken.current++; detailRequest.current++; };
  }, [sharedOnly]);

  useEffect(() => {
    loadDetail(selectedId);
    return () => { detailRequest.current++; };
  }, [selectedId]);

  function openCreateList() {
    setEditingList(null);
    setListForm({ ...EMPTY_LIST_FORM, shared: sharedOnly });
    setError("");
    setListDialogOpen(true);
  }

  function openEditList() {
    if (!detail?.list?.owner) return;
    setEditingList(detail.list);
    setListForm({
      name: detail.list.name || "",
      description: detail.list.description || "",
      shared: Boolean(detail.list.shared),
    });
    setError("");
    setListDialogOpen(true);
  }

  async function submitList(event) {
    event.preventDefault();
    const scope = scopeToken.current;
    setSaving(true);
    setError("");
    try {
      const response = editingList
        ? await updateProblemList(editingList.id, { ...listForm, shared: sharedOnly || listForm.shared })
        : await createProblemList({ ...listForm, shared: sharedOnly || listForm.shared });
      const nextDetail = requireData(response);
      if (scope !== scopeToken.current) return;
      setListDialogOpen(false);
      const nextId = await loadLists(nextDetail.list.id);
      if (scope !== scopeToken.current) return;
      if (nextId === nextDetail.list.id) setDetail(nextDetail);
      else await loadDetail(nextId);
    } catch (requestError) {
      if (scope === scopeToken.current) setError(requestError.message || "题单保存失败");
    } finally {
      if (scope === scopeToken.current) setSaving(false);
    }
  }

  async function removeList() {
    if (!detail?.list?.owner) return;
    if (!window.confirm(`确定删除题单“${detail.list.name}”及其中的全部题目吗？`)) return;
    const scope = scopeToken.current;
    setSaving(true);
    setError("");
    try {
      requireData(await deleteProblemList(detail.list.id));
      if (scope !== scopeToken.current) return;
      setDetail(null);
      await loadLists(null);
    } catch (requestError) {
      if (scope === scopeToken.current) setError(requestError.message || "题单删除失败");
    } finally {
      if (scope === scopeToken.current) setSaving(false);
    }
  }

  function openAddProblem() {
    setEditingProblem(null);
    setProblemForm(EMPTY_PROBLEM_FORM);
    setError("");
    setProblemDialogOpen(true);
  }

  function openEditProblem(problem) {
    setEditingProblem(problem);
    setProblemForm({
      title: problem.title || "",
      link: problem.link || "",
      problemKey: problem.problemKey || "",
      rating: problem.rating ?? "",
      tags: problem.tags || "",
      note: problem.note || "",
    });
    setError("");
    setProblemDialogOpen(true);
  }

  async function submitProblem(event) {
    event.preventDefault();
    if (!detail || !canAddProblem(detail.list)) return;
    const scope = scopeToken.current;
    setSaving(true);
    setError("");
    const payload = {
      ...problemForm,
      rating: problemForm.rating === "" ? null : Number(problemForm.rating),
    };
    try {
      const response = editingProblem
        ? await updateProblemListItem(detail.list.id, editingProblem.id, payload)
        : await addProblemListItem(detail.list.id, payload);
      requireData(response);
      if (scope !== scopeToken.current) return;
      setProblemDialogOpen(false);
      await Promise.all([loadDetail(detail.list.id), loadLists(detail.list.id)]);
    } catch (requestError) {
      if (scope === scopeToken.current) setError(requestError.message || "题目保存失败");
    } finally {
      if (scope === scopeToken.current) setSaving(false);
    }
  }

  async function removeProblem(problem) {
    if (!window.confirm(`确定从题单中移除“${problem.title}”吗？`)) return;
    const scope = scopeToken.current;
    setSaving(true);
    setError("");
    try {
      requireData(await deleteProblemListItem(detail.list.id, problem.id));
      if (scope !== scopeToken.current) return;
      await Promise.all([loadDetail(detail.list.id), loadLists(detail.list.id)]);
    } catch (requestError) {
      if (scope === scopeToken.current) setError(requestError.message || "题目移除失败");
    } finally {
      if (scope === scopeToken.current) setSaving(false);
    }
  }

  return {
    lists, myLists, sharedLists, selectedId, setSelectedId, detail, loading, detailLoading,
    saving, error, setError, openCreateList, openEditList, removeList,
    openAddProblem, openEditProblem, removeProblem,
    listEditor: {
      open: listDialogOpen, onOpenChange: open => !saving && setListDialogOpen(open),
      editing: Boolean(editingList), form: listForm, setForm: setListForm, sharedOnly,
      saving, error, onSubmit: submitList,
    },
    problemEditor: {
      open: problemDialogOpen, onOpenChange: open => !saving && setProblemDialogOpen(open),
      editing: Boolean(editingProblem), form: problemForm, setForm: setProblemForm,
      saving, error, onSubmit: submitProblem,
    },
  };
}

function requireData(response) {
  if (response?.code !== 200) throw new Error(response?.message || "请求失败");
  return response.data;
}
