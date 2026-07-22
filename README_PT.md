<div align="center">
  <img src=".github/docs/img/dragonminez_big_logo.png" alt="Banner do DragonMine Z" width="70%" />
</div>

<div align="center">

[![Discord](https://img.shields.io/discord/1216429657273012415?style=for-the-badge&logo=Discord&logoColor=white&label=Discord&color=orange)](https://discord.gg/b5MgRNb3D7)
[![Patreon](https://img.shields.io/badge/Patreon-Apoiar-yellow?style=for-the-badge&logo=Patreon)](https://patreon.com/DragonMineZ)
[![Quadro de progresso](https://img.shields.io/badge/GitHub-Quadro%20de%20Progresso-red?style=for-the-badge&logo=GitHub)](https://github.com/orgs/DragonMineZ/projects/4)
[![CurseForge](https://img.shields.io/badge/CurseForge-Baixar-orange?style=for-the-badge)](https://www.curseforge.com/minecraft/mc-mods/dragonminez)
[![Modrinth](https://img.shields.io/badge/Modrinth-Baixar-green?style=for-the-badge)](https://modrinth.com/mod/dragonminez)

</div>

<div align="center">

[English](README.md) · [Español](README_ES.md) · **Português**

</div>

---

> ### ⚙️ Port não-oficial para 1.20.2
>
> Este repositório é um **port do DragonMineZ para Minecraft 1.20.2 (Forge 48.1.0)**, feito pela comunidade.
>
> **Por quê:** para o DragonMineZ rodar em servidores e modpacks de Minecraft 1.20.2 — a versão que introduziu a *fase de configuration* de networking, que muitos proxies e mods da era 1.20.2 usam.
>
> **Base:** acompanha o DragonMineZ mais recente — **v2.1.3** (commit [`3c58ae8`](https://github.com/DragonMineZ/dragonminez/commit/3c58ae869788a37fb42d0ed47a5494724a07735a) do upstream) — incluindo os fixes de GUI, texturas/assets, combate e animações mesclados no upstream até 2026-07-22. É mantido em sincronia com o upstream e re-portado por cima.
>
> **O que mudou:** o mod foi migrado para as APIs do 1.20.2 — fase de configuration de networking (`NetworkEvent.Context` → `CustomPayloadEvent.Context`, `ChannelBuilder`), receitas por `RecipeHolder`/codec, `AdvancementHolder`, `SavedData.Factory` (com data-fix types), mudanças de assinatura de GUI (`renderBackground`/`mouseScrolled`), API de skin, construtores de `ResourceLocation`, modelos `.geo.json` de armas no formato `1.12.0`, e atualização de dependências (GeckoLib 4.3.1, TerraBlender/Curios/JEI para 1.20.2). A escala de hitbox e altura-dos-olhos por forma (que usava o removido `EntityEvent.Size`) é preservada via um mixin de `Player`; um par de hooks exclusivos do GeckoLib 4.4+ fica inerte e marcado no código.

## Sobre

**DragonMine Z** é um mod imersivo de **Minecraft Forge 1.20.2** em desenvolvimento, inspirado na obra mais famosa de Akira Toriyama: [Dragon Ball](https://en.dragon-ball-official.com/).

Nosso objetivo é levar a experiência completa de Dragon Ball para o Minecraft: personagens personalizados, raças, transformações, habilidades, atributos, conteúdo de história, dimensões, NPCs, estruturas e uma experiência de sobrevivência renovada.

O projeto ainda está em desenvolvimento ativo, então os recursos podem mudar conforme continuamos melhorando o mod.

---

## ✨ Recursos

- Crie seu próprio personagem inspirado em Dragon Ball.
- Escolha entre várias raças, incluindo Saiyajin, Humano, Namekuseijin, Demônio do Frio, Bio-Androide e Majin.
- Personalize sua aparência com cabelo, olhos, pele, cores de aura e muito mais.
- Progrida por atributos como Força, Poder de Golpe, Resistência, Vitalidade, Poder de Ki e Energia.
- Desbloqueie habilidades, transformações, técnicas de combate e novas formas de ficar mais forte.
- Explore novas áreas, dimensões, encontros com NPCs, estruturas e conteúdo de história.
- Viva a sobrevivência do Minecraft com um sistema de progressão inspirado em Dragon Ball.

---

## 🖼️ Showcase

<table>
  <tr>
    <td align="center" width="50%">
      <img src=".github/docs/img/red-ribbon-robots.png" alt="Prévia de inimigos robôs da Red Ribbon" width="100%" />
      <br />
      <strong>Encontro com Robôs da Red Ribbon</strong>
      <br />
      Prévia de dois inimigos robôs da Red Ribbon sendo adicionados ao DragonMine Z como parte do elenco de inimigos e encontros de combate.
    </td>
    <td align="center" width="50%">
      <img src=".github/docs/img/namek-background.png" alt="Prévia de cenário inspirado em Namek" width="100%" />
      <br />
      <strong>Prévia do ambiente de Namek</strong>
      <br />
      Mostra de um cenário inspirado em Namek, apresentando a atmosfera e a direção visual do mod.
    </td>
  </tr>
</table>

---

## 🚀 Download

Você pode baixar DragonMine Z nas nossas páginas oficiais:

- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/dragonminez)
- [Modrinth](https://modrinth.com/mod/dragonminez)

DragonMine Z foi feito para **Minecraft Forge 1.20.2**.

---

## 🫴 Apoie o projeto

Quer previews antecipadas e atualizações extras dos bastidores?

Entre no nosso [Patreon](https://patreon.com/DragonMineZ) para apoiar o desenvolvimento e ter acesso a previews e benefícios semelhantes. Seu apoio nos ajuda a continuar melhorando DragonMine Z e criando mais conteúdo para a comunidade.

Você também pode entrar no nosso [servidor do Discord](https://discord.gg/b5MgRNb3D7) para acompanhar o desenvolvimento, reportar bugs, sugerir ideias e conversar com a comunidade.

---

## 🗺️ Roadmap

<div align="center">
  <img src=".github/docs/img/roadmap.png" alt="Roadmap do DragonMine Z" />
</div>

Você também pode acompanhar nosso [Quadro de Progresso público no GitHub](https://github.com/orgs/DragonMineZ/projects/4).

---

## 🤝 Contribuindo

Gostaria de ajudar? Legal! Confira o [guia de contribuição](.github/CONTRIBUTING.md) para começar.

Você pode ajudar com:

- Contribuições de código
- Relatórios de bugs
- Sugestões de recursos
- Traduções
- Documentação
- Modelos, animações, construções e outros trabalhos criativos

---

## 🎯 Uso de terceiros

### Sons

Alguns sons são usados a partir do [Zapsplat](https://www.zapsplat.com/) e do [Freesound](https://freesound.org/):

- [Dragon Ball Scouter/Tracker Remade.wav](https://freesound.org/s/518004/) por Pablobd | Licença: Attribution 3.0
- [A Symphony for Akira Toriyama](https://www.youtube.com/watch?v=xNVEkSerkU0) por GLADIUS | Licença: CC-BY License

### Agradecimentos

Este projeto inclui código do [GeckoLib](https://github.com/bernie-g/geckolib), licenciado sob a Licença MIT.

Copyright © 2026 GeckoThePecko. Consulte o arquivo [`THIRD_PARTY_LICENSES`](THIRD_PARTY_LICENSES) para mais detalhes.

---

## ✨ Autores

### Desenvolvedores

- [Yuseix](https://github.com/yuseix300) | *Fundador e Programador*
- [ezShokkoh](https://github.com/Shokkoh) | *Fundador e Programador*
- [Bruno](https://github.com/Bruneitor123) | *Co-Fundador, Programador, e Community Admin*

### Contribuidores

- [Bati2ra](https://github.com/Bati2ra) | *Programador*
- [KyoSleep](https://github.com/KyoSleep1) | *Programador*
- JotaJoestar | *Modelador e Animador*
- Toji71_ | *Construtor*

---

## Licença

2026, DragonMine Z.

Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob os termos da Licença Pública Geral GNU, conforme publicada pela Free Software Foundation, seja a versão 3 da Licença ou, a seu critério, qualquer versão posterior.

[Licença Pública Geral GNU v3.0](https://github.com/DragonMineZ/dragonminez/blob/main/LICENSE)