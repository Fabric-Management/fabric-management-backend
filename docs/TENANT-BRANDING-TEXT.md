# Tenant SVG text preparation

`POST /api/v1/common/tenant/settings/branding/preview` accepts the same multipart file (512 KB)
and colour mode as upload. It requires `members:manage`, does not write assets, and returns
sanitised SVG bytes with `no-store`. Static artwork needs no renderer. Text artwork passes the
closed static/text preflight, exact local font checks, bounded Inkscape outlining, then the
original vector-only sanitizer. Upload still refuses live text. The UI uploads only the
prepared file after explicit visual approval; cancelling or any preparation failure leaves the
saved asset unchanged. No temporary server asset, public preview URL or font upload is created.

The worker requires Python 3.9+, Inkscape 1.2+ and Fontconfig (`fc-match`). The runtime Dockerfile
installs these with DejaVu fonts. Fonts must be installed in the renderer's system font directories
with the appropriate licence and refreshed with `fc-cache -f`. Proprietary fonts (including Songti
SC) are not bundled or substituted. Mount approved fonts read-only under `/usr/local/share/fonts`
when deploying. Exact family, weight/slant and glyph coverage are checked; an absent first-choice
font, synthetic face or missing glyph returns `TENANT_BRANDING_FONT_UNAVAILABLE` (422).
Complex typography not supported by the safe subset returns `TENANT_BRANDING_TEXT_UNSUPPORTED`.
Missing tools, saturation or timeout return `TENANT_BRANDING_CONVERSION_UNAVAILABLE` (503).

For native development configure these executable paths before starting the backend:

```sh
export BRANDING_OUTLINE_PYTHON=python3
export BRANDING_OUTLINE_INKSCAPE=/Applications/Inkscape.app/Contents/MacOS/inkscape
export BRANDING_OUTLINE_FONT_MATCH=/opt/homebrew/bin/fc-match
```

Use paths appropriate to the host. This feature does not install fonts or system tools at runtime.
Each conversion uses an isolated temporary working/profile/cache directory, non-shell argument
vectors, a two-process concurrency cap, a 15 second parent deadline, worker CPU/output/file-descriptor
limits and a 1 GiB address-space cap on Linux. Descendants are terminated on failure and temporary
files are cleaned up. The worker runs under the application OS user (non-root in the Dockerfile);
it is not a separate container security boundary. External references, scripts, images and embedded
fonts are rejected before starting it. The input document is not logged.

Operator verification (not run by the implementer):

```sh
./mvnw test -Dtest=BrandSvgSanitizerServiceTest,BrandSvgPreparationServiceTest,TenantBrandingControllerTest,LocalBrandSvgOutlineClientTest
python3 -m unittest discover -s src/test/python -p 'test_brand_outline.py'
UPDATE_OPENAPI=true ./mvnw verify -Dtest=ConstitutionArchTest -Dit.test=TenantBrandingIT,OpenApiExportIT
./mvnw verify
```

Then in the frontend:

```sh
pnpm run sync:spec
pnpm run generate:api
pnpm run typecheck
pnpm test -- --runInBand --testPathPatterns='(brand-asset-editor|brand-asset-preview|use-tenant-branding)'
```

The additive OpenAPI snapshot is prepared alongside controller annotations; re-export it with the
above operator command before committing backend first, then sync/regenerate the frontend.
Manually exercise known installed fonts, absent fonts, outlined-only artwork, declined approval,
light/dark backgrounds and tenant switching. No test/build/verification or commit was run here.


Suggested commits (operator-owned):

- Backend: `feat(tenant): prepare SVG text outlines before artwork approval`
- Frontend: `feat(branding): require preview approval before uploading artwork`

Renderer references: [Inkscape CLI](https://inkscape.org/zh-hant/doc/inkscape-man.html?switchlang=zh-hant)
(`--export-text-to-path`, `--export-plain-svg`) and
[Fontconfig font matching](https://fontconfig.pages.freedesktop.org/fontconfig/fontconfig-user.html).
