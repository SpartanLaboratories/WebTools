# Project website

The source of <https://spartanlaboratories.github.io/WebTools/> - a static, dependency-free
landing page for WebTools. There is no build step and no site generator: the files in this
directory are published as they are.

| File | What it is |
|---|---|
| `index.html` | The whole page - hero, features, architecture, modules, install, quick start, testing, links |
| `assets/styles.css` | One stylesheet. Theme colours are CSS custom properties on `:root`, redefined for `[data-theme="light"]` |
| `assets/site.js` | Theme toggle, mobile nav, install tabs, copy buttons, scroll-spy, and a small syntax highlighter. The page stays readable if it never loads |
| `assets/favicon.svg` | Favicon |
| `assets/brand/` | **Not committed here.** The Spartan Laboratories logo for the header, copied from [`docs/brand/`](../docs/brand/) at publish time. That folder is the one home of the Spartan Laboratories brand assets, shared with the Dokka theme |
| `404.html` | Not-found page (absolute `/WebTools/...` paths, because GitHub serves it from any depth) |
| `.nojekyll` | Not committed here: added at publish time by the Pages workflow, as the organization's standard requires. It stops Jekyll dropping names that start with an underscore; today this deployment runs no Jekyll and Dokka writes no such name, so it is a safeguard |
| `robots.txt`, `sitemap.xml` | Crawler hints |

## How it is published

[`.github/workflows/pages.yml`](../.github/workflows/pages.yml) runs on every push to `master`.
It generates the Dokka publication (`./gradlew dokkaGeneratePublicationHtml`), copies this
directory to the site root, `docs/brand/` to `assets/brand/` and the Dokka output to `api/`,
adds `.nojekyll`, then deploys with `actions/deploy-pages`. So `/` is this page and `/api/` is
the full API reference.

Enabling it once for the repository, with GitHub Actions as the Pages source:
`gh api -X POST repos/SpartanLaboratories/WebTools/pages -f build_type=workflow`, or
**Settings -> Pages -> Build and deployment -> Source -> GitHub Actions**.

## Working on it locally

Any static file server will do - the page uses relative paths. Copy the brand assets in
first, or the header logo will be missing (`website/assets/brand/` is git-ignored):

```bash
mkdir -p website/assets/brand && cp -r docs/brand/. website/assets/brand/
python3 -m http.server 8000 --directory website
# then open http://localhost:8000/
```

`/api/` will 404 locally unless you generate the docs into it first:

```bash
./gradlew dokkaGeneratePublicationHtml
cp -r build/dokka/html website/api      # git-ignored; do not commit
```

## When cutting a release

The versions on the page are written by hand: the Modules table (`data-version` cells), the
Install snippets and nothing else. Bump them together with the `version` of the module's build
file at each release:

```bash
grep -rn "2\.0\.1\|1\.0\.0" website/index.html
```

Each module versions on its own: `webtools-udp`, `webtools-http`, `webtools-feeds`,
`webtools-scraping` and `webtools-browser` have separate rows.
