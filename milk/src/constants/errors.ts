const createError = (message: string) => JSON.stringify({ error: message })
export const INVALID_CURSOR_QUERY_STRING = createError('Provided `cursor` query string is not a valid date.')