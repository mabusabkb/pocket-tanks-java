# Pocket Tanks (Java)

A two-player artillery game written in plain Java (Swing), inspired by the classic *Pocket Tanks*. Two tanks take turns firing at each other across destructible, randomly generated terrain. Everything you see and hear (graphics, sound effects, music, weapon icons) is generated in code, so there are no image or audio files and no external libraries.

<!-- Add a screenshot: save it as screenshot.png in this folder, then remove these comment markers.
![Gameplay screenshot](screenshot.png)
-->

## Features

- **Two players on one computer**, taking turns
- **7 weapons**, each with its own behavior (see the table below)
- **Weapon-picking screen** where each player builds an arsenal of 10 weapons
- **Destructible terrain**: craters, dirt mounds, tanks that drop into holes
- **Wind** that changes every turn
- **Tank movement** with a limited fuel amount each turn
- **Score system**: points for hitting the enemy, and the closer the hit, the more points
- **Choose your tank color** from 9 presets or any custom color
- **Animated visuals**: parallax mountains and clouds, tanks that tilt with the ground, particles, shockwaves, and screen shake
- **Sound effects and looping background music**, both can be switched off
- **Game over screen** with the winner when all weapons have been used

## Weapons

| Weapon | Crater size | Max damage | What it does |
|---|---|---|---|
| Small Shell | 30 | 35 | Basic shell |
| Big Shell | 60 | 60 | Big crater, big damage |
| Triple Shot | 25 | 30 each | Three shells fanned out |
| Bouncer | 35 | 40 | Bounces twice, then explodes |
| Roller | 30 | 45 | Lands, then rolls toward a tank |
| Dirt Mover | 45 | 0 | Piles up a hill instead of digging |
| Nuke | 100 | 90 | Giant blast |

## How to play

1. On the first screen, each player picks **10 weapons** (max 3 of each kind), or click **Random Pick**.
2. Choose a tank color for each player, then click **START GAME**.
3. On your turn:
    - Set the **Angle** and **Power** with the sliders (a dotted line shows your aim)
    - Pick a **Weapon** from the dropdown
    - Optionally use **Move <** / **Move >** (limited by Fuel)
    - Click **FIRE**
4. Watch the wind in the top bar. It pushes every shell sideways.
5. Each shot uses up one weapon. When all weapons are used, the player with the most points wins.

### Buttons

| Button | Action |
|---|---|
| FIRE | Fire the selected weapon |
| Move < / Move > | Move your tank (uses fuel) |
| New Game | Go back to the weapon-picking screen |
| End Game | Stop now and show the result |
| Quit | Close the game |
| Sound / Music | Turn effects or background music on or off |

## Run it

You need **JDK 11 or newer**.

### With IntelliJ IDEA
1. Open the project folder in IntelliJ.
2. Open `src/PocketTanks.java`.
3. Click the green arrow next to `main` and choose **Run 'PocketTanks.main()'**.

### From the command line
```
javac -d out src/PocketTanks.java
java -cp out PocketTanks
```

## Project structure

```
src/PocketTanks.java   all game code
```

Inside the file, the code is organized into:

- `PocketTanks`: game state, physics, scoring, and drawing
- `WeaponIcon`: the weapon pictures, drawn with Java 2D
- `ArsenalPanel`: the weapon and color picking screen
- `Sound`: synthesized sound effects
- `Music`: the synthesized background music loop

## Adding your own weapon

1. Add a line to the `Weapon` table at the top of `PocketTanks.java`:
   ```java
   MEGA("Mega Bomb", 90, 80, 1, 0, Kind.NORMAL, "Huge crater"),
   ```
2. Add a matching `case` in `WeaponIcon.paintIcon` so it gets a picture.

## Ideas for the future

- Keyboard controls
- More weapons and a bigger arsenal
- Play against the computer
- Different terrain types and backgrounds

## Credits

Inspired by *Pocket Tanks* by Blitwise. This is an independent fan project. It is not affiliated with Blitwise, and all graphics and sounds here are original and generated in code.

Made by [mabusabkb](https://github.com/mabusabkb).
