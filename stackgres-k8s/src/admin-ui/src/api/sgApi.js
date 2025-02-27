import axios from 'axios';

const baseURL = '/stackgres';

const resources = {
  auth_type: '/auth/type',
  can_i: '/auth/rbac/can-i',
  login: '/auth/login',
  logout: '/auth/logout',
  namespaces: '/namespaces',
  sgclusters: '/sgclusters',
  sgshardedclusters: '/sgshardedclusters',
  sgstreams: '/sgstreams',
  sginstanceprofiles: '/sginstanceprofiles',
  sgbackups: '/sgbackups',
  sgpgconfigs: '/sgpgconfigs',
  sgpoolconfigs: '/sgpoolconfigs',
  sgobjectstorages: '/sgobjectstorages',
  sgscripts: '/sgscripts',
  sgdistributedlogs: '/sgdistributedlogs',
  sgdbops: '/sgdbops',
  storageclasses: '/storageclasses',
  extensions: '/extensions',
  applications: '/applications',
  sgconfigs: '/sgconfigs',
  users: '/users',
  roles: '/roles',
  clusterroles: '/clusterroles'
};

export default {
    
  get(resource) {
    return axios.get(baseURL + resources[resource])
  },

  create(resource, data, dryRun = false) {
    return axios.post(baseURL + resources[resource] + (dryRun ? '?dryRun=true' : ''), data)
  },

  update(resource, data, dryRun = false) {
    return axios.put(baseURL + resources[resource] + (dryRun ? '?dryRun=true' : ''), data)
  },

  delete(resource, data, dryRun = false) {
    return axios.delete(baseURL + resources[resource] + (dryRun ? '?dryRun=true' : ''), data)
  },

  getResourceDetails(resource, namespace, name, details = '', query = '') {
    return axios.get(baseURL + '/namespaces/' + namespace + resources[resource] + '/' + name + (details.length ? ('/' + details + query) : '') )
  },

  getCustomResource(endpoint) {
    return axios.get(baseURL + endpoint)
  },

  createCustomResource(endpoint, data = null) {
    return axios.post(baseURL + endpoint, data)
  },

  getPostgresVersions(flavor) {
    return axios.get(baseURL + '/version/postgresql?flavor=' + flavor)
  },

  getPostgresExtensions(version, flavor = 'vanilla') {
    return axios.get(baseURL + '/extensions/' + version + '?flavor=' + flavor )
  },

}
