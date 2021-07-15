package part2.rmi.puzzle;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import part2.akka.messages.update.UpdateNextMsg;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.CropImageFilter;
import java.awt.image.FilteredImageSource;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@SuppressWarnings("serial")
public class PuzzleBoard extends JFrame {
	
	private final int rows, columns;
    private final JPanel board;
    private List<Tile> tiles = new ArrayList<>();
	
	private SelectionManager selectionManager = new SelectionManager();

    public PuzzleBoard(final int rows, final int columns, final String imagePath) {
    	this.rows = rows;
		this.columns = columns;

    	setTitle("Puzzle");
        setResizable(false);

        board = new JPanel();
        board.setBorder(BorderFactory.createLineBorder(Color.gray));
        board.setLayout(new GridLayout(rows, columns, 0, 0));
        getContentPane().add(board, BorderLayout.CENTER);

        createTiles(imagePath);
        paintPuzzle();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                //TODO dire che sono morto
                System.exit(0);
            }
        });
    }


    private void createTiles(final String imagePath) {
		final BufferedImage image;

        try {
            image = ImageIO.read(new File(imagePath));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not load image", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        final int imageWidth = image.getWidth(null);
        final int imageHeight = image.getHeight(null);

        int position = 0;

        final List<Integer> randomPositions = new ArrayList<>();
        IntStream.range(0, rows*columns).forEach(item -> { randomPositions.add(item); });
        Collections.shuffle(randomPositions);

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
            	final Image imagePortion = createImage(new FilteredImageSource(image.getSource(),
                        new CropImageFilter(j * imageWidth / columns,
                        					i * imageHeight / rows,
                        					(imageWidth / columns),
                        					imageHeight / rows)));

                tiles.add(new Tile(imagePortion, position, randomPositions.get(position)));
                position++;
            }
        }
	}

    private void paintPuzzle() {
        Optional<Tile> selectedTile = selectionManager.getSelectedTile();
    	board.removeAll();

    	Collections.sort(tiles);

    	tiles.forEach(tile -> {
    		final TileButton btn = new TileButton(tile);
            board.add(btn);
            btn.setBorder(BorderFactory.createLineBorder(selectedTile.isPresent() && tile == selectedTile.get() ? Color.red : Color.gray));
            btn.addActionListener(actionListener -> {
            	selectionManager.selectTile(tile, () -> {
            		paintPuzzle();
                	checkSolution();
                    //TODO aggiornare gli altri
            	});
            });
    	});
    	
    	pack();
        setLocationRelativeTo(null);
    }

    private void checkSolution() {
    	if(tiles.stream().allMatch(Tile::isInRightPlace)) {
    		JOptionPane.showMessageDialog(this, "Puzzle Completed!", "", JOptionPane.INFORMATION_MESSAGE);
            //TODO dire che sono morto
    		System.exit(0);
    	}
    }

    public List<Integer> getCurrentPositions() {
        return this.tiles.stream()
                .sorted((t1, t2) -> t1.getOriginalPosition() < t2.getOriginalPosition() ? -1 : (t1.getOriginalPosition() == t2.getOriginalPosition() ? 0 : 1))
                .map(tile -> tile.getCurrentPosition())
                .collect(Collectors.toList());
    }

    public void setCurrentPositions(final List<Integer> positions){
        for (int i=0; i<positions.size(); i++){
            int finalI = i;
            this.tiles.stream().filter(tile -> tile.getOriginalPosition() == finalI).findAny().get().setCurrentPosition(positions.get(i));
        }
        paintPuzzle();
        checkSolution();
    }
}
