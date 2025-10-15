/// <reference types="next" />
/// <reference types="next/image-types/global" />
/// <reference types="next/navigation-types/compat/navigation" />

declare module "*.svg" {
  const content: string;
  export default content;
}
