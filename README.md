# DAI Practical Work 2 - Card Battle Game
![Maven](https://img.shields.io/badge/build-Maven-blue?logo=apachemaven)
![Java](https://img.shields.io/badge/java-21-orange?logo=openjdk)

## Tables of contents
- [Tables of contents](#tables-of-contents)
- [Description](#description)
  - [Commands](#commands)
  - [Rules](#Rules)
  - [If enough time](#if-enough-time)
- [Clone and build](#clone-and-build)
  - [For Linux / MacOS](#for-linux--macos)
  - [For Windows](#for-windows)
- [Usage](#usage)
- [Utilisation IA](#utilisation-ia)
- [Authors](#authors)
- [References](#references)

## Description

### Commands:

- **connect <UserName>**: requests a connection with a unique username.
- **disconnect**: disconnects from the server. However, if the player is currently in a match, this counts as a surrender.
- **challenge <player2>**: sends a challenge request to another player.
- **accept y/N**: the challenged player can accept (y) or decline (N). If no answer is given within 10 seconds, the request is automatically declined.
- **getPlayers**: displays all available players (those who are not currently playing or being challenged).
- **rules**: displays the game rules.
- **surrender**: ends the current match. If time allows, scores are updated (0 points for the player who surrenders, and the winner’s points are added to their total score).
- **play <cardNum 1 to 5>**: indicates which card the player wants to play.

### Rules:
Each card is unique and has both a type and a value between 1 and 9.
**The 4 card types are:**
- 🔪 : Knife
- 🔫 : Gun
- 👊 : Fist
- 🧪 : Acid

Each player has a hand of 5 cards drawn from a deck of 36 cards.
Each turn, a player has 30 seconds to play one card (otherwise, a random card is automatically played).

**Win Conditions:**

There are three possible victory types per round:

1. Aggressive Victory:
    The player with the superior type wins.
    → Winner: +2 pts | Loser: +0 pts

    Type hierarchy:
    - Acid beats Gun
    - Gun beats Knife
    - Knife beats Fist
    - Fist beats Acid

2. Equal-Type Victory:
    If both cards share the same type, the higher value wins.
    → Winner: +1 pt | Loser: +0 pt

3. Opposite Victory:
    If the types differ but are not in an aggressive relationship, the lower value wins.
    → Winner: +0 pt | Loser: -1 pt
    If both values are equal → no points are awarded.

**End of the Game:**

At the end of each round, 5 new cards are dealt to both players.
A match ends when:

- a player **reaches 7 points**, or

- after **13 rounds** (in which case both players lose).

### If enough time
- Several matches can take place in parallel.
- Add mmr ( score was made with score all game divide by the number of games) (integrated in getplayer)
   - mmr updapte each end game ( special if a player surrender, take 0 point)
- add rank commande : return a ranking with all players connected.
for information : @Arnaut 

## Clone and build
These following instructions will help you to get a copy of the project up and running on your local machine for development and testing purposes.

1. Clone the repository
<div style="display: flex; gap: 20px;">
  <pre><code class="language-bash">
# Clone with SSH
git clone git@github.com:Ischi-Leyre/dai-pw2.git
  </code></pre>

  <pre><code class="language-bash">
# Clone with HTTPS
git clone https://github.com/Ischi-Leyre/dai-pw2.git
  </code></pre>
</div>

2. Navigate to the project directory
~~~bash
cd dai-pw2
~~~

### For Linux / MacOS
Download the dependencies (only for the first time)
~~~bash
./mvnw dependency:go-offline
~~~

Build the project and generate the jar file
~~~bash
./mvnw clean package
~~~

### For Windows
Download the dependencies (only for the first time)
~~~PowerShell
mvnw.cmd dependency:go-offline
~~~

Build the project and generate the jar file
~~~PowerShell
mvnw.cmd clean package
~~~

> [!NOTE]
> 
> If you use the IDE IntelliJ, yon can directly run the configuration **make jar file application** to automatic build the project and generate the jar file.

## Usage

**TODO**

## Utilisation IA
- ChatGPT :
  - Issue template: correction and help for the structure.
  - README: help for the integration HTML code (i.e. footer)
  - Code: generate the Java doc of Class / function.

- GitHub Copilot:
  - commit: for the commits made in browsers, name and description

- Reverso:
  - spelling, syntax, and reformulation : README, GitHub and comment in code: 
    - README
    - GitHub
    - Code: function and block comment

<footer style="padding: 1rem; background-color: rgba(0,0,0,0); border-top: 1px solid rgba(0,0,0,0);">
  <div style="display: flex; justify-content: center; gap: 4rem; flex-wrap: wrap; text-align: center;">
    <div>
    <h3 id="authors">Authors</h3>
    <p>
        <strong>
        <a href="https://github.com/Ischim">Ischi Marc</a>
        </strong>
        <br>
        <strong>
        <a href="https://github.com/Arnaut">Leyre Arnaut</a>
        </strong>
    </p>
    </div>
    <div>
    <h3 id="references">References</h3>
    <p>
        <a href="https://picocli.info/" target="_blank" rel="noopener noreferrer">
            <img    src="https://picocli.info/images/logo/horizontal.png"
                    alt="PicoCLI"
                    style="width: 105px; height: 39px">
        </a>
    </p>
    </div>
  </div>

  <div style="margin-top: 1rem;">
    <a href="https://github.com/Ischi-Leyre/dai-pw2-card-jitSUS" target="_blank" rel="noopener noreferrer">
        <img src="Documents/images/card-jitSUS.png"
             alt="Project logo"
            style="width: 80px; height: 100px; display: block; margin: 0 auto;">
    </a>
  </div>
</footer>
