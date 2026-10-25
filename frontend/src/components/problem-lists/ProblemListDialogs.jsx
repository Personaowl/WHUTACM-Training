import { useRef } from "react";
import * as Dialog from "@radix-ui/react-dialog";
import { X } from "lucide-react";

function DialogFrame({ open, onOpenChange, title, description, children }) {
  const returnFocus = useRef(null);
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-[80] bg-[#081627]/45 backdrop-blur-[2px]" />
        <Dialog.Content
          onOpenAutoFocus={() => { returnFocus.current = document.activeElement; }}
          onCloseAutoFocus={(event) => {
            event.preventDefault();
            const target = returnFocus.current?.isConnected ? returnFocus.current : document.getElementById("main-content");
            target?.focus({ preventScroll: true });
          }}
          className="fixed left-1/2 top-1/2 z-[81] max-h-[90vh] w-[calc(100%-2rem)] max-w-xl -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-2xl border border-border bg-white p-5 shadow-[0_28px_90px_rgba(8,22,39,0.25)] sm:p-6">
          <div className="flex items-start justify-between gap-4">
            <div><Dialog.Title className="m-0 text-xl font-bold text-text-primary">{title}</Dialog.Title><Dialog.Description className="sr-only">{description}</Dialog.Description></div>
            <Dialog.Close asChild><button type="button" className="rounded-lg p-2 text-text-secondary hover:bg-bg-secondary" aria-label="关闭"><X size={18} /></button></Dialog.Close>
          </div>
          {children}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

export function ListEditorDialog({ open, onOpenChange, editing, form, setForm, sharedOnly, isAdmin, saving, error, onSubmit }) {
  return (
    <DialogFrame open={open} onOpenChange={onOpenChange} title={editing ? "编辑题单" : "创建题单"} description="题单就是一级分类，例如“区间 DP”或“暑假补题”。">
      <form className="mt-5 stack-y-4" onSubmit={onSubmit}>
        <Field label="题单名称"><input required maxLength={80} value={form.name} onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))} placeholder="例如：区间 DP" className="form-input" /></Field>
        <Field label="简介（可选）"><textarea maxLength={500} rows={3} value={form.description} onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))} placeholder="记录这个题单的训练目标或使用说明" className="form-input resize-none" /></Field>
        {!sharedOnly && isAdmin && (
          <label className="flex cursor-pointer items-start gap-3 rounded-xl border border-border bg-bg-secondary p-3">
            <input type="checkbox" checked={form.shared} onChange={(event) => setForm((current) => ({ ...current, shared: event.target.checked }))} className="mt-0.5 h-4 w-4 accent-[#17324f]" />
            <span><strong className="block text-sm text-text-primary">发布为只读题单</strong></span>
          </label>
        )}
        {error && <p role="alert" className="text-sm text-error">{error}</p>}
        <DialogActions saving={saving} submitText={editing ? "保存修改" : "创建题单"} />
      </form>
    </DialogFrame>
  );
}

export function ProblemEditorDialog({ open, onOpenChange, editing, form, setForm, saving, error, onSubmit }) {
  return (
    <DialogFrame open={open} onOpenChange={onOpenChange} title={editing ? "编辑题目" : "添加题目"} description="粘贴标准 Codeforces 链接后，可自动从本地题库补全题目资料。">
      <form className="mt-5 stack-y-4" onSubmit={onSubmit}>
        <Field label="题目链接"><input required maxLength={1000} type="url" value={form.link} onChange={(event) => setForm((current) => ({ ...current, link: event.target.value }))} placeholder="https://codeforces.com/problemset/problem/607/B" className="form-input" /></Field>
        <Field label="题目标题（CF 题库可自动补全）"><input maxLength={255} value={form.title} onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))} placeholder="Zuma" className="form-input" /></Field>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="题目编号（可选）"><input maxLength={64} value={form.problemKey} onChange={(event) => setForm((current) => ({ ...current, problemKey: event.target.value }))} placeholder="607-B" className="form-input" /></Field>
          <Field label="Rating（可选）"><input type="number" min="0" max="5000" value={form.rating} onChange={(event) => setForm((current) => ({ ...current, rating: event.target.value }))} placeholder="1800" className="form-input" /></Field>
        </div>
        <Field label="标签（可选，逗号分隔）"><input maxLength={1000} value={form.tags} onChange={(event) => setForm((current) => ({ ...current, tags: event.target.value }))} placeholder="dp, two pointers" className="form-input" /></Field>
        <Field label="备注（可选）"><textarea maxLength={1000} rows={3} value={form.note} onChange={(event) => setForm((current) => ({ ...current, note: event.target.value }))} placeholder="记录关键思路、易错点或重做计划" className="form-input resize-none" /></Field>
        {error && <p role="alert" className="text-sm text-error">{error}</p>}
        <DialogActions saving={saving} submitText={editing ? "保存修改" : "加入题单"} />
      </form>
    </DialogFrame>
  );
}

function Field({ label, children }) {
  return <label className="block"><span className="mb-1.5 block text-xs font-bold text-text-primary">{label}</span>{children}</label>;
}

function DialogActions({ saving, submitText }) {
  return (
    <div className="flex justify-end gap-2 border-t border-border pt-4">
      <Dialog.Close asChild><button type="button" className="min-h-10 rounded-xl border border-border bg-white px-4 text-sm font-bold hover:bg-bg-secondary">取消</button></Dialog.Close>
      <button type="submit" disabled={saving} className="button-primary min-w-28">{saving ? "保存中..." : submitText}</button>
    </div>
  );
}
