export const toDateString = (date: Date) => date.getUTCFullYear() + '/'
    + pad(2, date.getUTCMonth() + 1) + '/'
    + pad(2, date.getUTCDate())


const pad = (length: number = 2, number: number) => number.toString().padStart(length, "0")
export const toISOString = (date: Date) => date.toISOString().replace('Z', '+0000')
export const toTimeString = (date: Date) =>
    pad(2, date.getUTCHours()) + ':' +
    pad(2, date.getUTCMinutes()) + ':' +
    pad(2, date.getUTCSeconds()) + '.' +
    pad(3, date.getUTCMilliseconds()) +
    '+0000'