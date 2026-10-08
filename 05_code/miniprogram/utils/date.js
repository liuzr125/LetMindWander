function formatDate(date = new Date()) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function formatChineseDate(date = new Date()) {
  const weekdays = ['日', '一', '二', '三', '四', '五', '六'];
  return `${date.getMonth() + 1}月${date.getDate()}日 周${weekdays[date.getDay()]}`;
}

// Word-study business dates match the backend's Asia/Shanghai day regardless of device timezone.
function formatBusinessDate(date = new Date()) {
  const beijing = new Date(date.getTime() + 8 * 60 * 60 * 1000);
  return `${beijing.getUTCFullYear()}-${String(beijing.getUTCMonth() + 1).padStart(2, '0')}-${String(beijing.getUTCDate()).padStart(2, '0')}`;
}
function formatBusinessChineseDate(date = new Date()) {
  const beijing = new Date(date.getTime() + 8 * 60 * 60 * 1000);
  return `${beijing.getUTCMonth() + 1}月${beijing.getUTCDate()}日 周${['日','一','二','三','四','五','六'][beijing.getUTCDay()]}`;
}
module.exports = { formatDate, formatChineseDate, formatBusinessDate, formatBusinessChineseDate };
