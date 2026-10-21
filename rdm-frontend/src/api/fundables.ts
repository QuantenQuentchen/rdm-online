import api from './client'
import config from '../config'
import type { CommitmentRequest, Fundable, FundableComponent } from '../types'

export const fundablesApi = {
  list() {
    return api.get<Fundable[]>(config.endpoints.fundables)
  },
  get(id: number) {
    return api.get<Fundable>(`${config.endpoints.fundables}/${id}`)
  },
  getComponent(id: number) {
    return api.get<FundableComponent>(`${config.endpoints.fundables}/components/${id}`)
  },
  addInterest(componentId: number) {
    return api.post<FundableComponent>(`${config.endpoints.fundables}/components/${componentId}/interests`)
  },
  removeInterest(componentId: number, userId: number) {
    return api.del<null>(`${config.endpoints.fundables}/components/${componentId}/interests/${userId}`)
  },
  commit(componentId: number, request: CommitmentRequest) {
    return api.post<{ id: number }>(`${config.endpoints.fundables}/components/${componentId}/commitments`, request)
  },
}

export default fundablesApi
