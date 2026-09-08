const ENVIRONMENTS = {
  develop: {
    apiBaseUrl: 'https://7773ead3.r7.vip.cpolar.cn/api'
  },
  trial: {
    apiBaseUrl: 'https://trial-api.example.com/api'
  },
  release: {/*  */
    apiBaseUrl: 'https://api.example.com/api'
  }
};

function getMiniProgramEnv() {
  try {
    return wx.getAccountInfoSync().miniProgram.envVersion || 'develop';
  } catch (error) {
    return 'develop';
  }
}

const envVersion = getMiniProgramEnv();
const current = ENVIRONMENTS[envVersion] || ENVIRONMENTS.develop;

module.exports = {
  envVersion,
  apiBaseUrl: current.apiBaseUrl,
  requestTimeout: 10000
};
