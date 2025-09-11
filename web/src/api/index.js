import { get, post, put, del } from './client'

export const homeApi = {
  recommend: () => get('/home/recommend')
}

export const bookApi = {
  list: ({ pageNum = 1, pageSize = 10, categoryId } = {}) => get('/book/list', { pageNum, pageSize, categoryId }),
  search: ({ keyword, pageNum = 1, pageSize = 10 }) => get('/book/search', { keyword, pageNum, pageSize }),
  detail: (id) => get(`/book/${id}`)
}

export const chapterApi = {
  list: (bookId) => get(`/chapter/list/${bookId}`),
  read: (id) => get(`/chapter/${id}`)
}

export const categoryApi = {
  list: () => get('/category/list')
}

export const commentApi = {
  list: (bookId) => get('/comment/list', { bookId }),
  add: (payload) => post('/comment/add', payload),
  remove: (id) => del(`/comment/${id}`),
  mine: () => get('/comment/mine')
}

export const ratingApi = {
  summary: (bookId) => get(`/rating/${bookId}`),
  rate: (bookId, score) => post(`/rating/${bookId}`, { score })
}

export const shelfApi = {
  list: () => get('/bookshelf/list'),
  check: (bookId) => get('/bookshelf/check', { bookId }),
  add: (bookId) => post('/bookshelf/add', { bookId }),
  remove: (bookId) => post('/bookshelf/remove', { bookId })
}

export const historyApi = {
  list: () => get('/history/list'),
  last: (bookId) => get('/history/last', { bookId }),
  save: (payload) => post('/history/save', payload)
}

export const rankApi = {
  hot: (top = 20) => get('/rank/hot', { top }),
  click: (top = 20) => get('/rank/click', { top }),
  newest: (top = 20) => get('/rank/new', { top }),
  update: (top = 20) => get('/rank/update', { top })
}

export const userApi = {
  login: (payload) => post('/user/login', payload),
  register: (payload) => post('/user/register', payload),
  info: () => get('/user/info'),
  updateProfile: (payload) => put('/user/profile', payload),
  changePassword: (payload) => put('/user/password', payload)
}

export const signApi = {
  signIn: () => post('/sign/in'),
  month: () => get('/sign/month'),
  continuous: () => get('/sign/continuous')
}

export const captchaUrl = () => `/captcha/image?t=${Date.now()}`
