function queueNotice(queue) {
  if (!queue) return '学习队列暂时不可用，请稍后重试';
  const blocked = Number(queue.unavailableCount || 0);
  if (!queue.remaining && blocked)
    return `${blocked} 个词暂不可用，学习范围已保留，请稍后重试`;
  if (queue.remaining && !queue.current) {
    const wait = Math.max(0, Math.ceil(Number(queue.waitSeconds || 0)));
    return wait ? `还有 ${queue.remaining} 个词等待巩固，约 ${wait} 秒后再试` : '还有单词等待巩固，请刷新队列继续';
  }
  if (queue.allCompleted === false && !queue.remaining) return '学习进度尚未完成，请刷新后继续';
  if (queue.practiceMode === 'continuous' && queue.allCompleted)
    return queue.dailyTotal ? '今日单词全部已标记认识，学习完成' : '今日没有待学新词';
  return '本轮单词已学完';
}

module.exports = { queueNotice };
