const ENVIRONMENTS = {
  develop: {
    apiBaseUrl: 'https://44f0224c.r25.cpolar.top/api'
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
