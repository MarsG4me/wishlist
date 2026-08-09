/** Error shape of every backend error response: { error_code, message }. */
export class ApiError extends Error {
  readonly errorCode: string
  readonly status: number

  constructor(errorCode: string, message: string, status: number) {
    super(message)
    this.errorCode = errorCode
    this.status = status
  }
}
