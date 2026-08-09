import React from "react";

/**
 * What a suspended account still has running.
 *
 * A suspension stops new business but cannot stop what is already under way: somebody has paid
 * and is waiting for goods, or has received them and may still send them back. Without this on
 * screen an admin has no way to tell a suspension that is genuinely finished from one that still
 * has a delivery out - and closing the account on the second is how a customer loses a return
 * they were entitled to.
 *
 * Renders nothing at all when there is nothing to say, so it can sit in a table cell for every
 * row without adding noise to the ones still trading.
 */
const WindDownStatus = ({ data, compact = false }) => {
  if (!data) return null;

  const awaiting = data.ordersAwaitingFulfilment || 0;
  const inWindow = data.ordersWithinReturnWindow || 0;
  const openReturns = data.openReturns || 0;

  if (data.complete) {
    return (
      <div style={{ fontSize: compact ? '0.72rem' : '0.8rem', color: '#146C43', marginTop: '4px', fontWeight: 500 }}>
        ✓ Nothing outstanding — safe to close
      </div>
    );
  }

  const parts = [];
  if (awaiting > 0) parts.push(`${awaiting} order${awaiting === 1 ? '' : 's'} still in flight`);
  if (inWindow > 0) parts.push(`${inWindow} within return window`);
  if (openReturns > 0) parts.push(`${openReturns} open return${openReturns === 1 ? '' : 's'}`);

  const ends = data.lastObligationEnds ? new Date(data.lastObligationEnds) : null;

  return (
    <div style={{
      fontSize: compact ? '0.72rem' : '0.8rem',
      color: '#92400e',
      background: '#fffbeb',
      border: '1px solid #fde68a',
      borderRadius: '4px',
      padding: '4px 6px',
      marginTop: '4px',
      maxWidth: compact ? '200px' : 'none',
    }}>
      <div style={{ fontWeight: 600 }}>Winding down</div>
      <div>{parts.join(', ')}</div>
      {ends && (
        <div style={{ marginTop: '2px' }}>
          {/* Marked as an estimate when it rests on the ten-day settlement assumption rather than
              a return window simply running out. A projection shown as a fact is worse than no
              date, because it gets planned around. */}
          {data.lastObligationEstimated ? 'Expected to clear about ' : 'Last obligation ends '}
          {ends.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })}
          {data.lastObligationEstimated && (
            <span style={{ color: '#b45309' }}> (estimate)</span>
          )}
        </div>
      )}
    </div>
  );
};

export default WindDownStatus;
