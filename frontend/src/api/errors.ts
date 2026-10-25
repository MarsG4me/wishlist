/** Error shape of every backend error response: { error_code, message, link_index? }. */
export class ApiError extends Error {
  readonly errorCode: string
  readonly status: number
  readonly linkIndex?: number

  constructor(errorCode: string, message: string, status: number, linkIndex?: number) {
    super(message)
    this.errorCode = errorCode
    this.status = status
    this.linkIndex = linkIndex
  }
}
