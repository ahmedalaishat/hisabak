import React from 'react';

/**
 * SMS parse-status chip. Four states with distinct color + icon so they are
 * unmistakable at a glance.
 *   linked     → green, already imported (a human confirmed it, or a deterministic rule matched)
 *   unreviewed → amber, imported but nobody checked it (an AI parse the auto-confirm gate took)
 *   parsed     → blue, ready to import
 *   unparsed   → gray, no data extracted
 */
export function StatusChip({ status = 'parsed', style, ...rest }) {
  const map = {
    linked:     ['Linked',     'var(--income-soft)',  'var(--income)',  'link'],
    unreviewed: ['Unreviewed', 'var(--warning-soft)', 'var(--warning)', 'help'],
    parsed:     ['Parsed',     'var(--info-soft)',    'var(--info)',    'bolt'],
    unparsed:   ['Unparsed',   'var(--surface-sunken)', 'var(--text-tertiary)', 'help'],
  };
  const [label, bg, fg, icon] = map[status] || map.parsed;
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 5,
        height: 24,
        padding: '0 10px 0 8px',
        borderRadius: 'var(--r-pill)',
        background: bg,
        color: fg,
        fontFamily: 'var(--font-sans)',
        fontWeight: 600,
        fontSize: 12,
        lineHeight: 1,
        ...style,
      }}
      {...rest}
    >
      <span className="material-symbols-rounded" style={{ fontSize: 14 }}>{icon}</span>
      {label}
    </span>
  );
}
