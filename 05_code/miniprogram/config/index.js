const ENVIRONMENTS = {
  develop: {
    apiBaseUrl: 'https://7c3cc4b6.r26.cpolar.top/api'
  },
  trial: {
    apiBaseUrl: 'https://7c3cc4b6.r26.cpolar.top/api'
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
