/**
 * Commit convention: Gitmoji + Conventional Commits.
 *
 *   <gitmoji> <type>(<optional scope>)<optional !>: <subject>
 *   ✨ feat(core): add NIOSH dose calculator
 *
 * See CONTRIBUTING.md for the emoji ↔ type table.
 */

// One emoji, optionally with a variation selector (♻️) or ZWJ sequence (🧑‍💻); `:shortcode:` also accepted.
const EMOJI = String.raw`(?:\p{Extended_Pictographic}(?:\uFE0F|\u200D\p{Extended_Pictographic})*|:[a-z0-9_+-]+:)`;
const HEADER = new RegExp(String.raw`^(${EMOJI}) (\w+)(?:\(([\w\-./ ]+)\))?(!)?: (.+)$`, "u");

export default {
  extends: ["@commitlint/config-conventional"],
  parserPreset: {
    parserOpts: {
      headerPattern: HEADER,
      headerCorrespondence: ["emoji", "type", "scope", "breaking", "subject"],
    },
  },
  plugins: [
    {
      rules: {
        "header-gitmoji": ({ header }) => [
          HEADER.test(header ?? ""),
          "header must look like '✨ feat(scope): subject' (gitmoji, space, conventional header)",
        ],
      },
    },
  ],
  rules: {
    "header-gitmoji": [2, "always"],
    "scope-enum": [
      2,
      "always",
      [
        "core",
        "domain",
        "audio",
        "data",
        "ui",
        "designsystem",
        "android",
        "desktop",
        "web",
        "build",
        "ci",
        "deps",
        "docs",
        "release",
      ],
    ],
    "subject-case": [2, "never", ["sentence-case", "start-case", "pascal-case", "upper-case"]],
    "header-max-length": [2, "always", 100],
  },
  ignores: [(message) => message.startsWith("Initial commit")],
};
