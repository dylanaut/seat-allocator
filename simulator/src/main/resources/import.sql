-- Wagen
INSERT INTO wagen (wagen_id, zugnummer, halte)
VALUES ('W1', 'ICE 101', 'Hamburg,Hannover,Frankfurt,München');

-- Gruppenkontingent bis 2030-01-01T00:00:00Z
INSERT INTO gruppenkontingente (kontingent_id, wagen_id, freigabefrist)
VALUES ('GRP_TABLE_01', 'W1', '2030-01-01T00:00:00Z');

-- Layout: Reihe 0 und 11 sind die umgewandelten Endreihen mit je einem Rollstuhl- und Fahrradstellplatz,
-- Reihe 1 Ruhebereich (links), Reihen 4-7 Gruppenbereich,
-- Reihe 10 Kinderbereich (rechts)
INSERT INTO sitzplaetze (sitz_id, wagen_id, art, reihe, spalte, zone, kontingent_id)
VALUES
('RS1', 'W1', 'ROLLSTUHL', 0, 1, 'STANDARD', NULL),
('FR1', 'W1', 'FAHRRAD', 0, 2, 'STANDARD', NULL),
('1A', 'W1', 'SITZ', 1, 0, 'RUHE', NULL),
('1B', 'W1', 'SITZ', 1, 1, 'RUHE', NULL),
('1C', 'W1', 'SITZ', 1, 2, 'RUHE', NULL),
('1D', 'W1', 'SITZ', 1, 3, 'RUHE', NULL),
('2A', 'W1', 'SITZ', 2, 0, 'STANDARD', NULL),
('2B', 'W1', 'SITZ', 2, 1, 'STANDARD', NULL),
('2C', 'W1', 'SITZ', 2, 2, 'STANDARD', NULL),
('2D', 'W1', 'SITZ', 2, 3, 'STANDARD', NULL),
('3A', 'W1', 'SITZ', 3, 0, 'STANDARD', NULL),
('3B', 'W1', 'SITZ', 3, 1, 'STANDARD', NULL),
('3C', 'W1', 'SITZ', 3, 2, 'STANDARD', NULL),
('3D', 'W1', 'SITZ', 3, 3, 'STANDARD', NULL),
('4A', 'W1', 'SITZ', 4, 0, 'STANDARD', 'GRP_TABLE_01'),
('4B', 'W1', 'SITZ', 4, 1, 'STANDARD', 'GRP_TABLE_01'),
('4C', 'W1', 'SITZ', 4, 2, 'STANDARD', 'GRP_TABLE_01'),
('4D', 'W1', 'SITZ', 4, 3, 'STANDARD', 'GRP_TABLE_01'),
('5A', 'W1', 'SITZ', 5, 0, 'STANDARD', NULL),
('5B', 'W1', 'SITZ', 5, 1, 'STANDARD', NULL),
('5C', 'W1', 'SITZ', 5, 2, 'STANDARD', NULL),
('5D', 'W1', 'SITZ', 5, 3, 'STANDARD', NULL),
('6A', 'W1', 'SITZ', 6, 0, 'STANDARD', NULL),
('6B', 'W1', 'SITZ', 6, 1, 'STANDARD', NULL),
('6C', 'W1', 'SITZ', 6, 2, 'STANDARD', NULL),
('6D', 'W1', 'SITZ', 6, 3, 'STANDARD', NULL),
('7A', 'W1', 'SITZ', 7, 0, 'STANDARD', NULL),
('7B', 'W1', 'SITZ', 7, 1, 'STANDARD', NULL),
('7C', 'W1', 'SITZ', 7, 2, 'STANDARD', NULL),
('7D', 'W1', 'SITZ', 7, 3, 'STANDARD', NULL),
('8A', 'W1', 'SITZ', 8, 0, 'STANDARD', NULL),
('8B', 'W1', 'SITZ', 8, 1, 'STANDARD', NULL),
('8C', 'W1', 'SITZ', 8, 2, 'STANDARD', NULL),
('8D', 'W1', 'SITZ', 8, 3, 'STANDARD', NULL),
('9A', 'W1', 'SITZ', 9, 0, 'STANDARD', NULL),
('9B', 'W1', 'SITZ', 9, 1, 'STANDARD', NULL),
('9C', 'W1', 'SITZ', 9, 2, 'STANDARD', NULL),
('9D', 'W1', 'SITZ', 9, 3, 'STANDARD', NULL),
('10A', 'W1', 'SITZ', 10, 0, 'KINDER', NULL),
('10B', 'W1', 'SITZ', 10, 1, 'KINDER', NULL),
('10C', 'W1', 'SITZ', 10, 2, 'KINDER', NULL),
('10D', 'W1', 'SITZ', 10, 3, 'KINDER', NULL),
('RS2', 'W1', 'ROLLSTUHL', 11, 1, 'STANDARD', NULL),
('FR2', 'W1', 'FAHRRAD', 11, 2, 'STANDARD', NULL);
