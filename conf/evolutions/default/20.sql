# --- !Ups

CREATE INDEX selection_rate_index ON selection(rate);
CREATE INDEX selection_round_index ON selection(round);
CREATE INDEX selection_jury_id_index ON selection(jury_id);
CREATE INDEX selection_page_id_index ON selection(page_id);

CREATE INDEX monument_id_index ON monument(id);